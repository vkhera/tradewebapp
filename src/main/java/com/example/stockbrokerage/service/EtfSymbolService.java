package com.example.stockbrokerage.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class EtfSymbolService {

    private final Set<String> knownEtfSymbols;

    public EtfSymbolService(@Value("${countryetf:}") List<String> countryEtfs,
                            @Value("${leveragedetfs:}") List<String> leveragedEtfs,
                            @Value("${sectoretfs:}") List<String> sectorEtfs) {
        this.knownEtfSymbols = Stream.of(countryEtfs, leveragedEtfs, sectorEtfs)
            .flatMap(List::stream)
            .map(this::normalize)
            .filter(s -> !s.isBlank())
            .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    }

    public boolean isEtf(String symbol) {
        return knownEtfSymbols.contains(normalize(symbol));
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
