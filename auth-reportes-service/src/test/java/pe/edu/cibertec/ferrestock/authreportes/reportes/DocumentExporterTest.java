package pe.edu.cibertec.ferrestock.authreportes.reportes;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Collections;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import pe.edu.cibertec.ferrestock.authreportes.reportes.ReportModels.*;
import static org.assertj.core.api.Assertions.*;

class DocumentExporterTest {
    Table table=new Table("Ventas por período","Datos sintéticos",List.of("Nombre","Total"),List.of(List.of("=HYPERLINK(\"untrusted\")",new BigDecimal("12.50")),List.of("Ferretería Ñ",new BigDecimal("3.00")),List.of("Extremo",Long.MIN_VALUE)));
    @Test void xlsxOpensWithTextCellsRatherThanFormulas()throws Exception{
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(new DocumentExporter().xlsx(table)))){
            assertThat(book.getSheetAt(0).getRow(4).getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(book.getSheetAt(0).getRow(4).getCell(1).getNumericCellValue()).isEqualTo(12.5);
            assertThat(book.getSheetAt(0).getRow(5).getCell(0).getStringCellValue()).isEqualTo("Ferretería Ñ");
            assertThat(book.getSheetAt(0).getRow(6).getCell(1).getStringCellValue()).isEqualTo(Long.toString(Long.MIN_VALUE));
        }
    }
    @Test void pdfOpensAndContainsReadableSpanish()throws Exception{
        try(var document=Loader.loadPDF(new DocumentExporter().pdf(table))){
            assertThat(new PDFTextStripper().getText(document)).contains("Ventas por período","Ferretería Ñ");
        }
    }
    @Test void pendingCommercialReaderDoesNotInventRows(){
        assertThatThrownBy(() -> new UnconfiguredCommercialReader().lowStock(5,100)).hasMessage("CONTRATO_COMERCIAL_PENDIENTE");
    }
    @ParameterizedTest @ValueSource(strings={"=SUM(A1:A2)","+cmd","-cmd","@formula"})
    void allFormulaPrefixesRemainStrings(String value)throws Exception{
        var input=new Table("Title","Filter",List.of("Value"),List.of(List.of(value)));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(new DocumentExporter().xlsx(input)))){
            assertThat(book.getSheetAt(0).getRow(4).getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(book.getSheetAt(0).getRow(4).getCell(0).getStringCellValue()).isEqualTo(value);
        }
    }
    @Test void multiPagePdfRepeatsHeadings()throws Exception{
        var input=new Table("Multipage","2026-01-01",List.of("SKU","Importe"),Collections.nCopies(150,List.of("SKU_SYNTHETIC",new BigDecimal("12.50"))));
        try(var document=Loader.loadPDF(new DocumentExporter().pdf(input))){
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            for(int page=1;page<=document.getNumberOfPages();page++){
                var reader=new PDFTextStripper();reader.setStartPage(page);reader.setEndPage(page);assertThat(reader.getText(document)).contains("Multipage","SKU | Importe","2026-01-01");
            }
        }
    }
    @Test void unsupportedUnicodeIsReplacedInPdfButPreservedInXlsx()throws Exception{
        var input=new Table("Unicode","Filter",List.of("Value"),List.of(List.of("Ñá 😀 中文")));
        try(var document=Loader.loadPDF(new DocumentExporter().pdf(input))){assertThat(new PDFTextStripper().getText(document)).contains("Ñá ? ??");}
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(new DocumentExporter().xlsx(input)))){assertThat(book.getSheetAt(0).getRow(4).getCell(0).getStringCellValue()).isEqualTo("Ñá 😀 中文");}
    }
    @Test void emptyDocumentIsValid()throws Exception{
        var input=new Table("Empty","Filter",List.of("Value"),List.of());
        try(var document=Loader.loadPDF(new DocumentExporter().pdf(input))){assertThat(new PDFTextStripper().getText(document)).contains("Sin registros");}
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(new DocumentExporter().xlsx(input)))){assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(3);}
    }
    @Test void xlsxNearRowLimitReopens()throws Exception{
        var input=new Table("Limit","Filter",List.of("Value"),Collections.nCopies(5000,List.of("Synthetic")));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(new DocumentExporter().xlsx(input)))){assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(5003);}
        try(var document=Loader.loadPDF(new DocumentExporter().pdf(input))){assertThat(document.getNumberOfPages()).isGreaterThan(100);}
    }
    @Test void invalidRowsFailBeforeReturningAnArtifact(){
        var input=new Table("Bad","Filter",List.of("A","B"),List.of(List.of("one")));
        assertThatThrownBy(() -> new DocumentExporter().pdf(input)).hasMessage("REPORTE_INVALIDO");assertThatThrownBy(() -> new DocumentExporter().xlsx(input)).hasMessage("REPORTE_INVALIDO");
    }
    @Test void numericExtremesMustNotBecomeZeroInfinityOrRoundedIntegers()throws Exception{
        var input=new Table("Precision","Filter",List.of("Value"),List.of(List.of(new BigDecimal("1E+10000")),List.of(new BigDecimal("1E-10000")),List.of(8_999_999_999_999_999L),List.of(new BigDecimal("1234567890123456.78"))));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(new DocumentExporter().xlsx(input)))){
            for(int i=0;i<4;i++){
                var cell=book.getSheetAt(0).getRow(i+4).getCell(0);assertThat(cell.getCellType()).isEqualTo(CellType.STRING);assertThat(cell.getStringCellValue()).isEqualTo(input.rows().get(i).getFirst().toString());
            }
        }
    }
}
