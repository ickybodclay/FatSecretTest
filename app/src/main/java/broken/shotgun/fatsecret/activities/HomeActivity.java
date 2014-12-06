package broken.shotgun.fatsecret.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import broken.shotgun.fatsecret.R;
import broken.shotgun.fatsecret.utils.FatSecretUtils;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;


public class HomeActivity extends ActionBarActivity {
    private static final String ACCESS_TOKEN_MISSING = "gone";

    private static final String TAG = HomeActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        SharedPreferences pref = getSharedPreferences(FatSecretUtils.PREFERENCES_FILE, MODE_PRIVATE);
        String accessToken = pref.getString(FatSecretUtils.OAUTH_ACCESS_TOKEN_KEY, ACCESS_TOKEN_MISSING);

        if(accessToken.equals(ACCESS_TOKEN_MISSING)) {
            Intent login = new Intent(this, LoginActivity.class);
            startActivity(login);
            finish();
            return;
        }

        FatSecretUtils.setContext(this);

        TextView loggedInText = (TextView) findViewById(R.id.loggedInText);
        loggedInText.setText("auth token = " + pref.getString("oauth_access_token", ACCESS_TOKEN_MISSING));

        final TextView responseText = (TextView) findViewById(R.id.responseText);
        responseText.setText("Searching foods for bananas...");

        final Gson gson = new GsonBuilder().setPrettyPrinting().create();

        new Thread(new Runnable() {
            @Override
            public void run() {
                BufferedReader reader = null;
                try {
                    String signedFoodSearchUrl = FatSecretUtils.sign("http://platform.fatsecret.com/rest/server.api?method=foods.search&format=json&search_expression=bananas");

                    Log.d(TAG, "Signed foods.search URL = " + signedFoodSearchUrl);

                    HttpURLConnection foodSearchConnection = (HttpURLConnection) new URL(signedFoodSearchUrl).openConnection();
                    reader = new BufferedReader(new InputStreamReader(foodSearchConnection.getInputStream()));
                    final String json = reader.readLine();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            JsonObject response = gson.fromJson(json, JsonObject.class);
                            responseText.setText(gson.toJson(response));
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (OAuthExpectationFailedException e) {
                    e.printStackTrace();
                } catch (OAuthCommunicationException e) {
                    e.printStackTrace();
                } catch (OAuthMessageSignerException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_home, menu);
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
