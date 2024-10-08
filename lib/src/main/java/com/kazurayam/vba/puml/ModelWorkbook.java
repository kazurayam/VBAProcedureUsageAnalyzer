package com.kazurayam.vba.puml;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class ModelWorkbook {

    private static final Logger logger = LoggerFactory.getLogger(ModelWorkbook.class);

    private final Path workbookPath;
    private final Path sourceDirPath;
    private final SortedMap<String, VBAModule> modules;

    private String id;
    private Charset vbaSourceCharset = Charset.forName("MS932");

    private static final String SHEET_NAME = "ExportedModules";
    private final static ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(ModelWorkbook.class, new ModelWorkbookSerializer());
        module.addSerializer(VBAModule.class, new VBAModule.VBAModuleSerializer());
        mapper.registerModule(module);
    }

    public ModelWorkbook(Path workbookPath, Path sourceDirPath) throws IOException {
        this.workbookPath = workbookPath;
        InputStream is = Files.newInputStream(workbookPath);
        modules = this.loadModules(is);
        this.sourceDirPath = sourceDirPath;
        injectSourceIntoModules(modules, sourceDirPath);
    }

    public void setCharset(Charset charset) {
        this.vbaSourceCharset = charset;
    }

    public ModelWorkbook id(String id) {
        this.id = id;
        return this;
    }

    /**
     * inject VBASource objects into the VBAModule objects in the module variable
     */
    void injectSourceIntoModules(
            SortedMap<String, VBAModule> modules,
            Path sourceDirPath) throws IOException {
        // get the list of VBA source files in the given sourceDir
        SourceDirVisitor visitor = new SourceDirVisitor();
        Files.walkFileTree(sourceDirPath, visitor);
        List<Path> sourceFiles = visitor.getList();
        // iterate over all VBAModules
        for (String moduleName : modules.keySet()) {
            VBAModule module = modules.get(moduleName);
            String expectedSourceFileName =
                    module.getName() + module.getType().getFileExtension();
            boolean found = false;
            for (Path sourceFile : sourceFiles) {
                if (sourceFile.getFileName().toString()
                        .equals(expectedSourceFileName)) {
                    VBASource vbaSource = new VBASource(module.getName(), sourceFile);
                    vbaSource.setCharset(vbaSourceCharset);
                    module.setVBASource(vbaSource);
                    found = true;
                    break;
                }
            }
            if (!found) {
                logger.warn(String.format("VBA source %s is not found in the directory %s",
                        expectedSourceFileName, sourceDirPath));
            }
        }
    }

    public String getId() {
        return id;
    }

    public Path getWorkbookPath() {
        return workbookPath;
    }

    public Path getSourceDirPath() {
        return sourceDirPath;
    }

    public boolean containsKey(String name) {
        return modules.containsKey(name);
    }

    public SortedMap<String, VBAModule> getModules() {
        return this.modules;
    }

    public Set<String> keySet() {
        return modules.keySet();
    }

    public VBAModule getModule(String name) {
        if (modules.containsKey(name)) {
            return modules.get(name);
        } else {
            throw new IllegalArgumentException(
                    String.format("VBAModule named %s is not found in %s", name, modules.keySet()));
        }
    }

    SortedMap<String, VBAModule> loadModules(InputStream inputStream) throws IOException {
        SortedMap<String, VBAModule> modules = new TreeMap<>();
        org.apache.poi.ss.usermodel.Workbook wb = new XSSFWorkbook(inputStream);
        Sheet sheet = wb.getSheet(SHEET_NAME);
        for (Row row : sheet) {
            if (row.getRowNum() > 0) { // we will skip the first row = header
                // |Project|ModuleType|Module|Scope|ProcKind|Procedure|LineNo|Source|Comment|
                String project = row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL).getStringCellValue();
                String moduleType = row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL).getStringCellValue();
                String module = row.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL).getStringCellValue();
                String scope = row.getCell(3, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL).getStringCellValue();
                String procKind = row.getCell(4, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL).getStringCellValue();
                String procedure = row.getCell(5, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL).getStringCellValue();
                double dvalue = row.getCell(6, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL).getNumericCellValue();
                int lineNo = ((Double)dvalue).intValue();
                String source = row.getCell(7, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL).getStringCellValue();
                String comment = row.getCell(8, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL).getStringCellValue();
                VBAProcedure proc =
                        new VBAProcedure.Builder()
                                .project(project)
                                .moduleType(moduleType)
                                .module(module)
                                .scope(scope)
                                .procKind(procKind)
                                .procedure(procedure)
                                .lineNo(lineNo)
                                .source(source)
                                .comment(comment)
                                .build();
                //
                VBAModule vbaModule;
                if (!modules.containsKey(module)) {
                    vbaModule = new VBAModule(module, proc.getModuleType());
                } else {
                    vbaModule = modules.get(module);
                }
                vbaModule.add(proc);
                modules.put(module, vbaModule);
            }
        }
        // if the Workbook.id was not specified, then set the project name of the Cell(1,0) as default
        if (this.id != null) {
            this.id = sheet.getRow(1).getCell(0).getStringCellValue();
        }
        //
        return modules;
    }

    public SortedSet<FullyQualifiedVBAProcedureId> getAllFullyQualifiedProcedureId() {
        SortedSet<FullyQualifiedVBAProcedureId> allFQPI = new TreeSet<>();
        for (VBAModule module : modules.values()) {
            for (VBAProcedure procedure : module.getProcedures()) {
                FullyQualifiedVBAProcedureId fqpi =
                        new FullyQualifiedVBAProcedureId(this, module, procedure);
                allFQPI.add(fqpi);
            }
        }
        return allFQPI;
    }

    @Override
    public String toString() {
        //pretty print
        try {
            Object json = mapper.readValue(this.toJson(), Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String toJson() throws JsonProcessingException {
        // without indent
        return mapper.writeValueAsString(this);
    }


    /**
     *
     */
    public static class ModelWorkbookSerializer extends StdSerializer<ModelWorkbook> {
        public ModelWorkbookSerializer() { this(null); }
        public ModelWorkbookSerializer(Class<ModelWorkbook> t) { super(t); }
        @Override
        public void serialize(
                ModelWorkbook wb, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            jgen.writeStartObject();
            jgen.writeStringField("id", wb.getId());
            jgen.writeStringField("workbookPath", wb.getWorkbookPath().toString());
            jgen.writeStringField("sourceDirPath", wb.getSourceDirPath().toString());
            jgen.writeArrayFieldStart("modules");
            for (String name : wb.getModules().keySet()) {
                jgen.writeObject(wb.getModule(name));
            }
            jgen.writeEndArray();
            jgen.writeEndObject();
        }
    }
}
