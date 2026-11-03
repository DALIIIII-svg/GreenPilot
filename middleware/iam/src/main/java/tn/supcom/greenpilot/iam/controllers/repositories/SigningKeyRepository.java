package tn.supcom.greenpilot.iam.controllers.repositories;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;
import tn.supcom.greenpilot.iam.entities.SigningKey;

import java.util.List;

@Repository
public interface SigningKeyRepository extends BasicRepository<SigningKey, String> {
    List<SigningKey> findByActive(boolean active);
}
