package com.backend.repository;

import com.backend.model.NewsletterSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NewsletterSubscriberRepository extends JpaRepository<NewsletterSubscriber, Long> {

    Optional<NewsletterSubscriber> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT n.email FROM NewsletterSubscriber n WHERE n.status = 'ACTIVE'")
    List<String> findAllActiveSubscriberEmails();
}
