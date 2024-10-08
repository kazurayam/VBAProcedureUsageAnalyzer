package com.kazurayam.vba.puml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kazurayam.unittest.TestOutputOrganizer;
import com.kazurayam.vba.example.MyWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import static org.assertj.core.api.Assertions.assertThat;

public class IndexerTest {
    private static final Logger logger =
            LoggerFactory.getLogger(IndexerTest.class);

    private static final TestOutputOrganizer too =
            new TestOutputOrganizer.Builder(IndexerTest.class)
                    .outputDirectoryRelativeToProject("build/tmp/testOutput")
                    .subOutputDirectory(IndexerTest.class)
                    .build();

    private Path classOutputDir;
    private Indexer indexer;
    private FullyQualifiedVBAProcedureId referee;
    private VBAProcedureReference expectedReference;

    @BeforeTest
    public void beforeTest() throws IOException {
        classOutputDir = too.cleanClassOutputDirectory();
        indexer = new Indexer();
        indexer.setOptions(Options.KAZURAYAM);

        // FeePaymentCheck workbook
        ModelWorkbook wbFeePaymentCheck =
                new ModelWorkbook(
                        MyWorkbook.FeePaymentControl.resolveWorkbookUnder(),
                        MyWorkbook.FeePaymentControl.resolveSourceDirUnder())
                        .id(MyWorkbook.FeePaymentControl.getId());
        indexer.add(wbFeePaymentCheck);
        VBAModule md会費納入状況チェック = wbFeePaymentCheck.getModule("会費納入状況チェック");
        FullyQualifiedVBAModuleId referrer =
                new FullyQualifiedVBAModuleId(wbFeePaymentCheck, md会費納入状況チェック);
        VBASource referrerModuleSource = md会費納入状況チェック.getVBASource();
        VBASourceLine referrerSourceLine =
                new VBASourceLine(55,
                        "    Set memberTable = MbMemberTableUtil.FetchMemberTable(memberFile, \"R6年度\", ThisWorkbook)");

        // Member workbook
        ModelWorkbook wbMember =
                new ModelWorkbook(
                        MyWorkbook.Member.resolveWorkbookUnder(),
                        MyWorkbook.Member.resolveSourceDirUnder())
                        .id(MyWorkbook.Member.getId());
        indexer.add(wbMember);
        VBAModule mdAoMemberUtil =
                wbMember.getModule("MbMemberTableUtil");
        VBAProcedure prFetchMemberTable =
                mdAoMemberUtil.getProcedure("FetchMemberTable");
        referee = new FullyQualifiedVBAProcedureId(wbMember,
                        mdAoMemberUtil, prFetchMemberTable);

        //
        expectedReference =
                new VBAProcedureReference(referrer,
                        referrerModuleSource, referrerSourceLine, referee);
    }

    @Test
    public void test_getWorkbooks() {
        List<ModelWorkbook> workbookList = indexer.getWorkbooks();
        assertThat(workbookList).hasSize(2);
    }

    @Test
    public void test_findAllProcedureReferences() throws IOException {
        SortedSet<VBAProcedureReference> memo =
                indexer.findAllProcedureReferences();
        assertThat(memo).isNotNull();
        assertThat(memo.size()).isEqualTo(20);
        Path out = classOutputDir.resolve("test_findAllProcedureReferences.txt");
        PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out));
        for (VBAProcedureReference ref : memo) {
            pw.println(ref.toString());
        }
        pw.flush();
        pw.close();
    }

    /**
     * This is the most interesting part of this project!
     */
    @Test
    public void test_findProcedureReferenceTo() throws IOException {
        Set<VBAProcedureReference> references =
                indexer.findProcedureReferenceTo(referee);
        assertThat(references).isNotNull();
        assertThat(references).hasSize(4);
        assertThat(references).contains(expectedReference);
        Path out = classOutputDir.resolve("test_findProcedureReferenceTo.txt");
        PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out));
        for (VBAProcedureReference reference : references) {
            pw.println(reference);
        }
        pw.flush();
        pw.close();
    }

    @Test
    public void test_shouldIgnore_Initialize() throws IOException {
        ModelWorkbook wbBackbone =
                new ModelWorkbook(
                        MyWorkbook.Backbone.resolveWorkbookUnder(),
                        MyWorkbook.Backbone.resolveSourceDirUnder())
                        .id(MyWorkbook.Backbone.getId());
        VBAModule mdDocTransformer = wbBackbone.getModule("BbDocTransformer");
        VBAProcedure prInitialize =
                mdDocTransformer.getProcedure("Initialize");
        FullyQualifiedVBAProcedureId referee =
                new FullyQualifiedVBAProcedureId(wbBackbone, mdDocTransformer,
                        prInitialize);
        //
        assertThat(indexer.shouldIgnore(referee)).isTrue();
    }

    @Test
    public void test_shouldIgnore_Class_Initialize() throws IOException {
        ModelWorkbook wbCashbook =
                new ModelWorkbook(
                        MyWorkbook.Cashbook.resolveWorkbookUnder(),
                        MyWorkbook.Cashbook.resolveSourceDirUnder())
                        .id(MyWorkbook.Cashbook.getId());
        VBAModule mdCash = wbCashbook.getModule("Cash");
        VBAProcedure prClassInitialize =
                mdCash.getProcedure("Class_Initialize");
        FullyQualifiedVBAProcedureId referee =
                new FullyQualifiedVBAProcedureId(wbCashbook, mdCash,
                        prClassInitialize);
        //
        assertThat(indexer.shouldIgnore(referee)).isTrue();
    }

    @Test
    public void test_xref() {
        List<ModelWorkbook> workbookList = indexer.getWorkbooks();
        Set<VBAProcedureReference> foundReferences =
                indexer.xref(workbookList, referee);
        assertThat(foundReferences).isNotNull();
        assertThat(foundReferences).hasSize(4);
        assertThat(foundReferences).contains(expectedReference);
    }

    @Test
    public void test_toJson() throws JsonProcessingException {
        String json = indexer.toJson();
        assertThat(json).isNotNull();
        logger.debug("[test_toJson] " + json);
    }

    @Test
    public void test_toString() throws IOException {
        String json = indexer.toString();
        assertThat(json).isNotNull();
        Path out = classOutputDir.resolve("test_toString.json");
        Files.writeString(out, json);
    }
}
