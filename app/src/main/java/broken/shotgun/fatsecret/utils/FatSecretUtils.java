package broken.shotgun.fatsecret.utils;

import android.content.Context;
import android.content.SharedPreferences;

import broken.shotgun.fatsecret.R;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.signature.HmacSha1MessageSigner;
import oauth.signpost.signature.QueryStringSigningStrategy;

/**
 * Created by Chunk on 12/6/2014.
 */
public class FatSecretUtils {
    private static Context context;
    public static final String PREFERENCES_FILE = "FatSecret";
    public static final String OAUTH_ACCESS_TOKEN_KEY = "oauth_access_token";
    public static final String OAUTH_ACCESS_SECRET_KEY = "oauth_access_secret";

    public static void setContext(Context cxt) {
        context = cxt;
    }

    public static String sign(String url) throws OAuthCommunicationException, OAuthExpectationFailedException, OAuthMessageSignerException {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
        OAuthConsumer consumer = new CommonsHttpOAuthConsumer(context.getString(R.string.fatsecret_consumer_key), context.getString(R.string.fatsecret_consumer_secret));
        consumer.setMessageSigner(new HmacSha1MessageSigner());
        consumer.setSigningStrategy(new QueryStringSigningStrategy());
        consumer.setTokenWithSecret(prefs.getString(OAUTH_ACCESS_TOKEN_KEY, ""), prefs.getString(OAUTH_ACCESS_SECRET_KEY, ""));
        return consumer.sign(url);
    }
}
