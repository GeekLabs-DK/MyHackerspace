/*
 * Copyright (C) 2012 Aubort Jean-Baptiste (Rorist)
 * Licensed under GNU's GPL 3, see README
 */

package ch.fixme.status;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;
import android.content.Context;

import java.net.URL;
import java.net.HttpURLConnection;
import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.net.ssl.SSLContext;
import java.security.SecureRandom;

import de.duenndns.ssl.MemorizingTrustManager;

// From CommonsWare and Android Blog
// https://github.com/commonsguy/cw-android/tree/master/Internet
// http://android-developers.blogspot.ch/2010/07/multithreading-for-performance.html
public class Net {

    private final String USERAGENT = "Android/" + Build.VERSION.RELEASE + " ("
            + Build.MODEL + ") MyHackerspace/1.7.1";

    private HttpURLConnection mUrlConnection;

    public Net(String urlStr, Context ctxt) throws Throwable {
        // register MemorizingTrustManager for HTTPS
        MemorizingTrustManager.setKeyStoreFile("private", "sslkeys.bks");
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, MemorizingTrustManager.getInstanceList(ctxt), new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        // Create client
        URL url = new URL(urlStr);
        mUrlConnection = (HttpURLConnection) url.openConnection();
        mUrlConnection.setRequestProperty("User-Agent", USERAGENT);
    }

    public String getString() throws Throwable {
        // FIXME: check mUrlConnection.getResponseCode()
        InputStream is = null;
        try {
            is = mUrlConnection.getInputStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            StringBuilder str = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                str.append(line);
            }
            return str.toString();
        } finally {
            if (is != null) {
                is.close();
            }
            mUrlConnection.disconnect();
        }
    }

    public Bitmap getBitmap() throws Throwable {
        InputStream is = null;
        try {
            is = mUrlConnection.getInputStream();
            return BitmapFactory.decodeStream(new FlushedInputStream(is));
        } finally {
            if (is != null) {
                is.close();
            }
            mUrlConnection.disconnect();
        }
    }

    static class FlushedInputStream extends FilterInputStream {
        public FlushedInputStream(InputStream inputStream) {
            super(inputStream);
        }

        @Override
        public long skip(long n) throws IOException {
            long totalBytesSkipped = 0L;
            while (totalBytesSkipped < n) {
                long bytesSkipped = in.skip(n - totalBytesSkipped);
                if (bytesSkipped == 0L) {
                    int b = read();
                    if (b < 0) {
                        break; // we reached EOF
                    } else {
                        bytesSkipped = 1; // we read one byte
                    }
                }
                totalBytesSkipped += bytesSkipped;
            }
            return totalBytesSkipped;
        }
    }

}
