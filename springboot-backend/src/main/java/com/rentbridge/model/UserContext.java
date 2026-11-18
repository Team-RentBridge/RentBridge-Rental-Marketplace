package com.rentbridge.model;

public class UserContext {
    private Long userId;
    private String name;
    private String email;
    private String role;
    private Boolean isAdmin;

    public UserContext() {}

    public UserContext(Long userId, String name, String email, String role, Boolean isAdmin) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.isAdmin = isAdmin;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Boolean getIsAdmin() { return isAdmin; }
    public void setIsAdmin(Boolean isAdmin) { this.isAdmin = isAdmin; }
}
