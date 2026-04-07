package com.example.uccchat;
import java.util.Date;
public class UserModel {
    private boolean isOnline;
    private Date lastSeen;
    private String uid;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String studentId;
    private String course;
    private String photoUrl;



    // Required empty constructor for Firestore
    public UserModel() {}

    public UserModel(String uid, String username, String firstName, String lastName,
                     String email, String phone, String studentId,
                     String course, String photoUrl) {
        this.uid       = uid;
        this.username  = username;
        this.firstName = firstName;
        this.lastName  = lastName;
        this.email     = email;
        this.phone     = phone;
        this.studentId = studentId;
        this.course    = course;
        this.photoUrl  = photoUrl;
    }

    // Getters
    public String getUid()       { return uid; }
    public String getUsername()  { return username; }
    public String getFirstName() { return firstName; }
    public String getLastName()  { return lastName; }
    public String getEmail()     { return email; }
    public String getPhone()     { return phone; }
    public String getStudentId() { return studentId; }
    public String getCourse()    { return course; }
    public String getPhotoUrl()  { return photoUrl; }

    // Getters
    public boolean isOnline()   { return isOnline; }
    public Date getLastSeen()   { return lastSeen; }


    // Setters
    // Setters
    public void setOnline(boolean online)  { this.isOnline = online; }
    public void setLastSeen(Date lastSeen) { this.lastSeen = lastSeen; }
    public void setUid(String uid)           { this.uid = uid; }
    public void setUsername(String username) { this.username = username; }
    public void setFirstName(String f)       { this.firstName = f; }
    public void setLastName(String l)        { this.lastName = l; }
    public void setEmail(String email)       { this.email = email; }
    public void setPhone(String phone)       { this.phone = phone; }
    public void setStudentId(String s)       { this.studentId = s; }
    public void setCourse(String course)     { this.course = course; }
    public void setPhotoUrl(String p)        { this.photoUrl = p; }

    // Helper: full name
    public String getFullName() {
        return firstName + " " + lastName;
    }


}