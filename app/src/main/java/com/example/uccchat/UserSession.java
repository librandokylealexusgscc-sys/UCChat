package com.example.uccchat;

import android.net.Uri;

public class UserSession {
    public static String username;
    public static String password;
    public static String firstName;
    public static String lastName;
    public static String phone;
    public static String email;
    public static String studentId;
    public static Uri profilePicUri;
    public static boolean isFromFacebook = false;
    public static String facebookToken = null;

    // To this:
    public static String course;
    public static boolean facebookEmailAlreadyExists = false;
    public static String firebaseUid;
    public static String photoUrl;

    // Google Sign-In extras
    public static String googlePhotoUrl;
    public static boolean isFromGoogle = false;

}