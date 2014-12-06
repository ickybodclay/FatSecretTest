package broken.shotgun.fatsecret.activities;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import broken.shotgun.fatsecret.R;
import broken.shotgun.fatsecret.utils.FatSecretUtils;
import oauth.signpost.OAuth;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.http.HttpParameters;
import oauth.signpost.signature.HmacSha1MessageSigner;
import oauth.signpost.signature.QueryStringSigningStrategy;

public class LoginActivity extends ActionBarActivity {
    public static final String TAG = LoginActivity.class.getName();
    private static final String ACCESS_TOKEN_MISSING = "gone";

    private Button loginButton;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(FatSecretUtils.PREFERENCES_FILE, MODE_PRIVATE);
        String accessToken = prefs.getString(FatSecretUtils.OAUTH_ACCESS_TOKEN_KEY, ACCESS_TOKEN_MISSING);

        if(!accessToken.equals(ACCESS_TOKEN_MISSING)) {
            Intent home = new Intent(this, HomeActivity.class);
            startActivity(home);
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        loginButton = (Button) findViewById(R.id.loginButton);
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginButton.setEnabled(false);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        getOAuthToken();
                    }
                }).start();
            }
        });
    }

    private void getOAuthToken() {
        /*
        Android users: Do NOT use the DefaultOAuth* implementations on Android, since there's a bug
        in Android's java.net.HttpURLConnection that keeps it from working with some service
        providers.

        Instead, use the CommonsHttpOAuth* classes, since they are meant to be used with
        Apache Commons HTTP (that's what Android uses for HTTP anyway).
         */
        // fatsecret_consumer_key = REST API Consumer Key, fatsecret_consumer_secret = REST API Shared Secret
        final OAuthConsumer consumer = new CommonsHttpOAuthConsumer(getString(R.string.fatsecret_consumer_key), getString(R.string.fatsecret_consumer_secret));
        consumer.setMessageSigner(new HmacSha1MessageSigner());
        consumer.setSigningStrategy(new QueryStringSigningStrategy());

        HttpParameters requestTokenRequestParams = new HttpParameters();
        requestTokenRequestParams.put("oauth_callback", OAuth.OUT_OF_BAND);
        consumer.setAdditionalParameters(requestTokenRequestParams);

        try {
            String signedRequestTokenRequestUrl = consumer.sign("http://www.fatsecret.com/oauth/request_token");

            Log.d(TAG, "Signed request_token URL = " + signedRequestTokenRequestUrl);

            HttpURLConnection requestTokenUrlConnection = (HttpURLConnection) new URL(signedRequestTokenRequestUrl).openConnection();
            HttpParameters requestTokenResponseParams = OAuth.decodeForm(requestTokenUrlConnection.getInputStream());
            final String requestToken = requestTokenResponseParams.getFirst(OAuth.OAUTH_TOKEN);
            final String requestSecret = requestTokenResponseParams.getFirst(OAuth.OAUTH_TOKEN_SECRET);
            Log.d(TAG, "Request token = " + requestToken);
            Log.d(TAG, "Token secret = " + requestSecret);

            final String authorizeUrl = "http://www.fatsecret.com/oauth/authorize?oauth_token=" + requestToken;
            Log.d(TAG, "Authorize URL = " + authorizeUrl);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final Dialog authDialog = new Dialog(LoginActivity.this);
                    authDialog.setContentView(R.layout.auth_dialog);
                    WebView web = (WebView) authDialog.findViewById(R.id.webv);
                    web.getSettings().setJavaScriptEnabled(true);
                    web.loadUrl(authorizeUrl);
                    web.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageStarted(WebView view, String url, Bitmap favicon) {
                            super.onPageStarted(view, url, favicon);
                        }

                        @Override
                        public void onPageFinished(WebView view, String url) {
                            super.onPageFinished(view, url);
                            Log.d(TAG, "URL = " + url);
                            if (url.contains("postVerify")) {
                                Uri uri = Uri.parse(url);
                                final String verifyCode = uri.getQueryParameter("postVerify");
                                Log.i(TAG, "VERIFY : " + verifyCode);
                                authDialog.dismiss();

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        final ProgressDialog progressDialog = new ProgressDialog(LoginActivity.this);
                                        progressDialog.setIndeterminate(true);
                                        progressDialog.setMessage("Fetching access token...");
                                        progressDialog.show();

                                        new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                consumer.getRequestParameters().clear();
                                                HttpParameters authTokenRequestParams = new HttpParameters();
                                                authTokenRequestParams.put("oauth_token", requestToken);
                                                authTokenRequestParams.put("oauth_verifier", verifyCode);
                                                consumer.setAdditionalParameters(authTokenRequestParams);
                                                consumer.setTokenWithSecret(requestToken, requestSecret);

                                                try {
                                                    String signedAccessTokenUrl = consumer.sign("http://www.fatsecret.com/oauth/access_token");
                                                    Log.d(TAG, "Signed access_token URL = " + signedAccessTokenUrl);
                                                    HttpURLConnection accessTokenUrlConnection = (HttpURLConnection) new URL(signedAccessTokenUrl).openConnection();
                                                    HttpParameters accessTokenResponseParams = OAuth.decodeForm(accessTokenUrlConnection.getInputStream());

                                                    String token = accessTokenResponseParams.getFirst(OAuth.OAUTH_TOKEN);
                                                    String secret = accessTokenResponseParams.getFirst(OAuth.OAUTH_TOKEN_SECRET);
                                                    prefs.edit()
                                                            .putString(FatSecretUtils.OAUTH_ACCESS_TOKEN_KEY, token)
                                                            .putString(FatSecretUtils.OAUTH_ACCESS_SECRET_KEY, secret)
                                                            .apply();

                                                    Intent home = new Intent(LoginActivity.this, HomeActivity.class);
                                                    startActivity(home);
                                                    finish();
                                                } catch (OAuthMessageSignerException e) {
                                                    e.printStackTrace();
                                                } catch (OAuthExpectationFailedException e) {
                                                    e.printStackTrace();
                                                } catch (OAuthCommunicationException e) {
                                                    e.printStackTrace();
                                                } catch (MalformedURLException e) {
                                                    e.printStackTrace();
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                } finally {
                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            loginButton.setEnabled(true);
                                                            progressDialog.dismiss();
                                                        }
                                                    });
                                                }
                                            }
                                        }).start();
                                    }
                                });
                            } else if (url.contains("error")) {
                                Log.i(TAG, "authorize error");
                                Toast.makeText(getApplicationContext(), "Error Occured", Toast.LENGTH_SHORT).show();
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        loginButton.setEnabled(true);
                                        authDialog.dismiss();
                                    }
                                });
                            }
                        }
                    });

                    authDialog.setTitle("Authorize FatSecret");
                    authDialog.setCancelable(true);
                    authDialog.show();
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (OAuthExpectationFailedException e) {
            e.printStackTrace();
        } catch (OAuthCommunicationException e) {
            e.printStackTrace();
        } catch (OAuthMessageSignerException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_login, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
