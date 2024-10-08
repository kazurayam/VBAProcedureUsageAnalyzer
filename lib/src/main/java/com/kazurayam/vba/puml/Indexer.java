package com.kazurayam.vba.puml;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The Indexer class analyses a set of ModelWorkbook objects
 * to find out a set of ProcedureReferences found amongst the
 * workbooks. The Indexer supports 2 ways of indexing.
 * - Partial indexing; accept a single FullyQualifiedProcedureId as a referee,
 * scan all the rest if the other procedure refers to the referee. Will cache
 * the scanning result, and return a set of ProcedureReferences found.
 * - Whole indexing; do the partial index for all of candidate referees as one batch.
 */
public class Indexer {

    private static final Logger logger = LoggerFactory.getLogger(Indexer.class);

    private final List<ModelWorkbook> workbooks;
    private final SortedSet<VBAProcedureReference> memo;
    private Options options;

    private static final ObjectMapper mapper;
    static {
        mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Indexer.class,
                new IndexerSerializer());
        module.addSerializer(VBAProcedureReference.class,
                new VBAProcedureReference.VBAProcedureReferenceSerializer());
        module.addSerializer(VBASource.class,
                new VBASource.VBASourceSerializer());
        module.addSerializer(VBASourceLine.class,
                new VBASourceLine.VBASourceLineSerializer());
        module.addSerializer(FullyQualifiedVBAProcedureId.class,
                new FullyQualifiedVBAProcedureId.FullyQualifiedVBAProcedureIdSerializer());
        mapper.registerModule(module);
    }

    public Indexer() {
        workbooks = new ArrayList<>();
        memo = new TreeSet<>();
        options = Options.DEFAULT;
    }

    public void add(ModelWorkbook workbook) {
        this.workbooks.add(workbook);
    }

    public void setOptions(Options options) {
        this.options = options;
    }

    public List<ModelWorkbook> getWorkbooks() {
        return workbooks;
    }

    public SortedSet<VBAProcedureReference> findAllProcedureReferences() {
        for (ModelWorkbook workbook : workbooks) {
            for (FullyQualifiedVBAProcedureId fqpi :
                    workbook.getAllFullyQualifiedProcedureId()) {
                //
                SortedSet<VBAProcedureReference> foundSet =
                        findProcedureReferenceTo(fqpi);
                //
                for (VBAProcedureReference procRef : foundSet) {
                    if (!options.shouldExcludeModule(procRef.getReferee().getModule())) {
                        memo.add(procRef);
                    }
                }
            }
        }
        return memo;
    }

    /**
     * This method is the core of this project.
     */
    public SortedSet<VBAProcedureReference> findProcedureReferenceTo(
            FullyQualifiedVBAProcedureId referee) {
        if (!options.shouldIgnoreRefereeProcedure(referee)) {
            SortedSet<VBAProcedureReference> scanResultByReferee =
                    this.scanMemoByVBAProcedureReferee(referee);

            if (scanResultByReferee.isEmpty()) {
                SortedSet<VBAProcedureReference> crossReferences =
                        xref(workbooks, referee);
                memo.addAll(crossReferences);
                return crossReferences;
            } else {
                return scanResultByReferee;
            }
        } else {
            return new TreeSet<>();
        }
    }

    Boolean shouldIgnore(FullyQualifiedVBAProcedureId referee) {
        return options.shouldIgnoreRefereeProcedure(referee);
    }

    SortedSet<VBAProcedureReference> scanMemoByVBAProcedureReferee(FullyQualifiedVBAProcedureId referee) {
        SortedSet<VBAProcedureReference> found = new TreeSet<>();
        for (VBAProcedureReference ref : memo) {
            if (ref.getReferee().equals(referee)) { found.add(ref); }
        }
        return found;
    }

    /**
     * Given with the referee.
     * Scan all VBA source code of all modules inside all workbooks given.
     * If any match with id of the referee found, record it.
     * Return a set of VBAProcedureReferences recorded.
     */
    SortedSet<VBAProcedureReference> xref(List<ModelWorkbook> workbooks,
                                          FullyQualifiedVBAProcedureId referee) {
        SortedSet<VBAProcedureReference> result = new TreeSet<>();
        for (ModelWorkbook subjectWorkbook : workbooks) {
            for (String subjectModuleName : subjectWorkbook.getModules().keySet()) {
                VBAModule subjectModule = subjectWorkbook.getModule(subjectModuleName);

                VBASource moduleSource = subjectModule.getVBASource();
                if (moduleSource == null) {
                    throw new IllegalStateException(
                            String.format("Could not get VBA source of the module %s in the workbook %s",
                                    subjectModule.getName(), subjectWorkbook.getId()));
                }
                // let's scan the VBASource to see if it mentions the referee
                List<Pattern> patterns =
                        ProcedureNamePatternManager.createPatterns(
                                referee.getProcedureName());

                if (!patterns.isEmpty()) {

                    // Now we will try to match the List<Pattern>
                    // against the subject VBASource
                    List<VBASourceLine> linesFound = moduleSource.find(patterns);

                    if (!linesFound.isEmpty()) {
                        // the referrer subjectModule refers to the referee!
                        for (VBASourceLine line : linesFound) {
                            FullyQualifiedVBAModuleId referrer =
                                    new FullyQualifiedVBAModuleId(subjectWorkbook, subjectModule);
                            VBAProcedureReference reference =
                                    new VBAProcedureReference(referrer, moduleSource, line, referee);
                            result.add(reference);
                        }
                    }
                }
            }
        }
        return result;
    }

    public Iterator<VBAProcedureReference> iterator() {
        return memo.iterator();
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

    public String toJson() throws JsonProcessingException {
        return mapper.writeValueAsString(this);
    }

    /**
     *
     */
    private static class IndexerSerializer extends StdSerializer<Indexer> {
        public IndexerSerializer() { this(null); }
        public IndexerSerializer(Class<Indexer> t) {
            super(t);
        }
        @Override
        public void serialize(
                Indexer indexer, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            jgen.writeStartObject();                             //{
            jgen.writeArrayFieldStart("workbooks");      // "workbooks":[
            for (ModelWorkbook wb : indexer.getWorkbooks()) {
                jgen.writeString(wb.getId());
            }
            jgen.writeEndArray();                                //  ],
            jgen.writeArrayFieldStart("procedureReferences");     //    "workbooks": [
            Iterator<VBAProcedureReference> iter = indexer.iterator();
            while(iter.hasNext()) {
                VBAProcedureReference reference = iter.next();
                jgen.writeObject(reference);                            //      { ... },
            }
            jgen.writeEndArray();                                //    ]
            jgen.writeEndObject();                               //}
        }
    }
}
