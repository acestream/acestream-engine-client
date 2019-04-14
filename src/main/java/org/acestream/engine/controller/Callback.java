package org.acestream.engine.controller;

public interface Callback<T> {
    public void onSuccess(T result);
    public void onError(String err);
}
