package org.acestream.engine.controller;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.Nullable;

/**
 * This interface gives direct access to all engine features.
 * For use in our app only.
 */
public interface Engine {
    interface Factory {
        Engine getInstance();
    }

    void destroy();

    void addListener(EventListener listener);
    void removeListener(EventListener listener);
    void signIn();

    void startEngine();
    void signInAceStream(String login, String password, Callback<Boolean> result);

    void signInGoogleSilent(Callback<Boolean> result);
    Intent getGoogleSignInIntent(Activity activity);
    void signInGoogleFromIntent(Intent intent, Callback<Boolean> result);
}
