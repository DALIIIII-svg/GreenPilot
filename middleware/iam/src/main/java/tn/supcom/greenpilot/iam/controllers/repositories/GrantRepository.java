package tn.supcom.greenpilot.iam.controllers.repositories;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;
import tn.supcom.greenpilot.iam.entities.Grant;

@Repository
public interface GrantRepository extends BasicRepository<Grant, String> {
}
