package com.kazurayam.vba.puml;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VBA Source code file (*.bas, *.cls)
 */
public class VBASource implements Comparable<VBASource> {

    private final String moduleName;
    private final Path sourcePath;

    private List<String> code;
    private Charset vbaSourceCharset = Charset.forName("MS932");
    private boolean codeLoaded;

    private final static ObjectMapper mapper;
    static {
        mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(VBASource.class, new VBASourceSerializer());
        module.addSerializer(VBASourceLine.class, new VBASourceLine.VBASourceLineSerializer());
        mapper.registerModule(module);
    }

    public VBASource(String moduleName, Path sourcePath) {
        this.moduleName = moduleName;
        this.sourcePath = sourcePath;
        code = new ArrayList<>();
        codeLoaded = false;
    }

    public void setCharset(Charset charset) {
        this.vbaSourceCharset = charset;
    }

    public String getModuleName() {
        return moduleName;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public List<String> getCode() {
        return code;
    }

    /**
     *
     */
    public List<VBASourceLine> find(List<Pattern> patterns) {
        List<VBASourceLine> linesFound = new ArrayList<>();
        cache();
        for (Pattern ptn : patterns) {
            for (int i = 0; i < code.size(); i++) {
                String line = code.get(i);
                Matcher m = ptn.matcher(line);
                if (m.find()) {
                    VBASourceLine vbaSourceLine = new VBASourceLine(i, line);
                    vbaSourceLine.setMatcher(m);
                    vbaSourceLine.setFound(true);
                    linesFound.add(vbaSourceLine);
                }
            }
        }
        return linesFound;
    }

    private void cache() {
        if (!codeLoaded) {
            try {
                code = loadCode();
                codeLoaded = true;
            } catch (IOException e) {
                throw new RuntimeException(
                        String.format("Failed to load code of Module %s, Source %s", moduleName, sourcePath),
                        e);
            }
        }
    }

    /**
     * Read all lines in a .bas file (or a .cls file), which is encoded in Shift_JIS
     * on kazurayam's machine
     * @return List of all lines in a .bas file
     */
    List<String> loadCode() throws IOException {
        File f = sourcePath.toFile();
        BufferedReader rdr = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(f), vbaSourceCharset));
        List<String> lines = new ArrayList<>();
        String ln;
        while ((ln = rdr.readLine()) != null) {
            lines.add(ln);
        }
        return lines;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VBASource other = (VBASource) o;
        if (moduleName.equals(other.moduleName)) {
            return sourcePath.equals(other.sourcePath);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int result = moduleName.hashCode();
        result = 31 * result + sourcePath.hashCode();
        return result;
    }

    @Override
    public String toString() {
        // pretty printed
        try {
            Object json = mapper.readValue(this.toJson(), Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int compareTo(VBASource other) {
        int moduleNameComparison = moduleName.compareTo(other.moduleName);
        if (moduleNameComparison == 0) {
            return sourcePath.compareTo(other.sourcePath);
        } else {
            return moduleNameComparison;
        }
    }

    public String toJson() throws JsonProcessingException {
        // no indent
        return mapper.writeValueAsString(this);
    }


    public static class VBASourceSerializer extends StdSerializer<VBASource> {
        public VBASourceSerializer() {
            this(null);
        }

        public VBASourceSerializer(Class<VBASource> t) {
            super(t);
        }

        @Override
        public void serialize(
                VBASource vbaSource, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            jgen.writeStartObject();     // {
            jgen.writeStringField("moduleName",
                    vbaSource.getModuleName());
            jgen.writeStringField("sourcePath",
                    vbaSource.getSourcePath().toString());
            // toString()の結果のJSONにcodeを含めるとJSONが大きくなるが、役に立たない。
            // だからcodeを含めない。
            /*
            if (vbaSource.codeLoaded) {
                jgen.writeArrayFieldStart("code");
                for (String line : vbaSource.getCode()) {
                    jgen.writeString(line);
                }
                jgen.writeEndArray();
            }
             */
            jgen.writeEndObject();       // }
        }
    }
}
