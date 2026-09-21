package com.dp.deviceops.parser.semantic.release;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Declarative model-family routing stored inside one immutable parser release. */
public record ModelProfileCatalog(
        String schemaVersion,
        List<String> baseRuleSets,
        Profile genericProfile,
        List<Profile> profiles) {

    public ModelProfileCatalog {
        if (!"1.0.0".equals(schemaVersion)) {
            throw new IllegalArgumentException("model profile schemaVersion must equal 1.0.0");
        }
        baseRuleSets = ruleSets(baseRuleSets, "baseRuleSets");
        if (baseRuleSets.isEmpty()) {
            throw new IllegalArgumentException("baseRuleSets must not be empty");
        }
        genericProfile = Objects.requireNonNull(genericProfile, "genericProfile");
        profiles = List.copyOf(Objects.requireNonNull(profiles, "profiles"));
        if (!genericProfile.match().empty()) {
            throw new IllegalArgumentException("generic profile must not declare model matches");
        }
        Set<String> profileIds = new HashSet<>();
        if (!profileIds.add(genericProfile.profileId())) {
            throw new IllegalArgumentException("profileId must be unique");
        }
        List<String> exact = new ArrayList<>();
        List<String> prefixes = new ArrayList<>();
        for (Profile profile : profiles) {
            Objects.requireNonNull(profile, "profile");
            if (!profileIds.add(profile.profileId())) {
                throw new IllegalArgumentException("profileId must be unique");
            }
            if (profile.match().empty()) {
                throw new IllegalArgumentException("model profile must declare an exact or prefix match");
            }
            for (String candidate : profile.match().exact()) {
                String normalized = normalize(candidate);
                if (exact.contains(normalized)) {
                    throw new IllegalArgumentException("exact model matches must be unique");
                }
                exact.add(normalized);
            }
            profile.match().prefixes().forEach(value -> prefixes.add(normalize(value)));
        }
        for (int left = 0; left < prefixes.size(); left++) {
            for (int right = left + 1; right < prefixes.size(); right++) {
                if (prefixes.get(left).startsWith(prefixes.get(right))
                        || prefixes.get(right).startsWith(prefixes.get(left))) {
                    throw new IllegalArgumentException("model profile prefixes must not overlap");
                }
            }
        }
        for (String value : exact) {
            if (prefixes.stream().anyMatch(value::startsWith)) {
                throw new IllegalArgumentException("exact model match must not overlap a prefix");
            }
        }
    }

    public record Profile(String profileId, Match match, List<String> ruleSets) {
        public Profile {
            profileId = text(profileId, "profileId");
            match = match == null ? new Match(List.of(), List.of()) : match;
            ruleSets = ModelProfileCatalog.ruleSets(ruleSets, "ruleSets");
        }
    }

    public record Match(List<String> exact, List<String> prefixes) {
        public Match {
            exact = values(exact);
            prefixes = values(prefixes);
        }

        boolean empty() {
            return exact.isEmpty() && prefixes.isEmpty();
        }
    }

    private static List<String> ruleSets(List<String> values, String field) {
        List<String> copy = values(values);
        if (new HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException(field + " must not contain duplicates");
        }
        return copy;
    }

    private static List<String> values(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(value -> text(value, "value")).toList();
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static String text(String value, String field) {
        String text = Objects.requireNonNull(value, field).strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return text;
    }
}
