package org.acestream.engine.controller;

public interface EventListener {
    public void onSignIn(boolean success, boolean gotError);
    public void onGoogleSignInAvaialble(boolean available);
}
