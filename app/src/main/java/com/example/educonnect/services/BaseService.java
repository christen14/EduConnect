package com.example.educonnect.services;

import androidx.annotation.NonNull;

import com.example.educonnect.entities.BaseEntity;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.Filter;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class BaseService<T extends BaseEntity> {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String collection;

    public BaseService(String collection) {
        this.collection = collection;
    }

    public void getAll(Consumer<List<T>> onSuccess) {
        getInternal(db.collection(this.collection), onSuccess);

    }

    public void get(Filter filter, Consumer<List<T>> onSuccess) {
        getInternal(db.collection(this.collection).where(filter), onSuccess);
    }
    public void getSorted(Filter filter, String field, Consumer<List<T>> onSuccess) {
        getInternal(db.collection(this.collection).where(filter).orderBy(field), onSuccess);
    }

    public void create(T entity, Consumer<T> onSuccess) {
        Map<String, Object> document;
        try {
            document = this.toDocument(entity);
        } catch (Exception ex) {
            System.out.println(ex);
            return;
        }

        db.collection(this.collection).add(document).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
            @Override
            public void onSuccess(DocumentReference documentReference) {
                T newEntity = null;
                try {
                    newEntity = createFromDocument(documentReference.getId(), document);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                onSuccess.accept(newEntity);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                System.out.println("Error adding document: " + e);
            }
        });
    }

    public void update(String id, T entity, Runnable onSuccess) {
        Map<String, Object> document;
        try {
            document = this.toDocument(entity);
        } catch (Exception ex) {
            System.out.println(ex);
            return;
        }

        db.collection(this.collection).document(id).update(document).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                onSuccess.run();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                System.out.println("Error updating document: " + e);
            }
        });
    }

    public void delete(String id, Runnable onSuccess) {
        db.collection(this.collection).document(id).delete()
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        System.out.println("Error deleting document: " + e);
                    }
                });
    }

    private void getInternal(Query query, Consumer<List<T>> onSuccess) {
        query.get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if (!task.isSuccessful()) {
                    System.out.println("Error getting documents: " + task.getException());
                    return;
                }

                List<T> result = new ArrayList<>();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    try {
                        result.add(createFromDocument(document.getId(), document.getData()));
                    } catch (Exception ex) {
                        System.out.println(ex);
                        break;
                    }
                }

                onSuccess.accept(result);
            }
        });
    }

    Map<String, Object> toDocument(T entity) throws IllegalAccessException {
        Map<String, Object> document = new HashMap<>();
        Class<T> clazz = ((Class) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0]);
        for (Field field : clazz.getDeclaredFields()) {
            String key = field.getName();

            field.setAccessible(true);
            Object value = field.get(entity);
            field.setAccessible(false);

            document.put(key, value);
        }

        return document;
    }

    T createFromDocument(String id, Map<String, Object> document) throws IllegalAccessException, InstantiationException {
        Class<T> clazz = ((Class) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0]);
        T instance = clazz.newInstance();
        instance.setId(id);

        for (Map.Entry<String, Object> entry : document.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            Field field;
            try {
                field = clazz.getDeclaredField(key);
            } catch (NoSuchFieldException ex) {
                System.out.println("Field " + key + " not found, skipping");
                continue;
            }

            field.setAccessible(true);
            field.set(instance, value);
            field.setAccessible(false);
        }

        return instance;
    }
}
