package org.xmlmate;

import org.xmlmate.execution.UseCase;

public class XMLMate {

    public static void main(String[] args) {
        // process command line arguments to determine the use case
        UseCase useCase = XMLProperties.determineUseCase(args);
        // load schema, index classes, prebuild automata, inspect schema
        XMLProperties.initialize();
        // run use case
        useCase.run();
    }
}
