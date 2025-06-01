package com.example.educonnect.services;

import com.example.educonnect.entities.User;
import com.google.firebase.firestore.Filter;

import java.util.List;
import java.util.function.Consumer;

public class UserService extends BaseService<User> {
    public UserService() {
        super("users");
    }
    /**
     * Retrieves the User document where "email" == given email.
     */
    public void getByEmail(String email, Consumer<List<User>> onSuccess) {
        Filter filter = Filter.equalTo("email", email);
        super.get(filter, onSuccess);
    }
}
