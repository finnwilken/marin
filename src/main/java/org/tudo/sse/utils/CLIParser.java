package org.tudo.sse.utils;

import org.tudo.sse.CLIException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class CLIParser {

    public abstract void parseArguments(String[] args);

    protected int nextArgAsInt(String[] args, int i) throws CLIException {
        if(i + 1 < args.length) {
            try{
                return Integer.parseInt(args[i + 1]);
            } catch(NumberFormatException e) {
                throw new CLIException(args[i], e.getMessage());
            }
        } else {
            throw new CLIException(args[i]);
        }
    }

    protected long[] nextArgAsLongPair(String[] args, int i) throws CLIException {
        long[] toReturn = new long[2];
        if(i + 1 < args.length) {
            String[] ints = args[i + 1].split(":");
            if(ints.length == 2) {
                try {
                    toReturn[0] = Long.parseLong(ints[0]);
                    toReturn[1] = Long.parseLong(ints[1]);
                } catch(NumberFormatException e) {
                    throw(new CLIException(args[i], e.getMessage()));
                }
            } else {
                throw(new CLIException("Correct format: first:second", args[i]));
            }
        } else {
            throw(new CLIException("Missing argument: first:second", args[i]));
        }
        return toReturn;
    }

    protected Path nextArgAsPath(String[] args, int i) throws CLIException {
        if(i + 1 < args.length) {
            return Paths.get(args[i + 1]);
        } else {
            throw(new CLIException("Missing argument: path/to/file", args[i]));
        }
    }

    protected Path nextArgAsRegularFileReference(String[] args, int i) throws CLIException {
        final Path path = nextArgAsPath(args, i);
        if(Files.isRegularFile(path))
            return path;
        else
            throw new CLIException("Expected an existing file but got: " + path, args[i]);
    }

    protected Path nextArgAsDirectoryReference(String[] args, int i) throws CLIException {
        final Path path = nextArgAsPath(args, i);
        if(Files.isDirectory(path))
            return path;
        else
            throw new CLIException("Expected an existing directory but got: " + path, args[i]);
    }

}
