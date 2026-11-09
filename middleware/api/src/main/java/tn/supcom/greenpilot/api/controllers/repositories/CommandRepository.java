package tn.supcom.greenpilot.api.controllers.repositories;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;
import tn.supcom.greenpilot.api.entities.Command;

import java.util.List;

/** Jakarta Data repository; Eclipse JNoSQL generates the implementation. */
@Repository
public interface CommandRepository extends BasicRepository<Command, String> {

    /** Returns commands for the specified site. */
    List<Command> findBySiteId(String siteId);
}
