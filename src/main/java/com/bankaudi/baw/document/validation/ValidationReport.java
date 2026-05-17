package com.bankaudi.baw.document.validation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class ValidationReport {
    private final Set<String> placeholders;
    private final Set<String> warnings;
    private final Set<String> errors;

    public ValidationReport(Set<String> placeholders, Set<String> warnings, Set<String> errors) {
        this.placeholders = Collections.unmodifiableSet(new LinkedHashSet<>(placeholders));
        this.warnings = Collections.unmodifiableSet(new LinkedHashSet<>(warnings));
        this.errors = Collections.unmodifiableSet(new LinkedHashSet<>(errors));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public Set<String> getPlaceholders() {
        return placeholders;
    }

    public Set<String> getWarnings() {
        return warnings;
    }

    public Set<String> getErrors() {
        return errors;
    }
}
