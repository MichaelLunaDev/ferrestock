package pe.edu.cibertec.ferrestock.authreportes.reportes;

import java.io.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import pe.edu.cibertec.ferrestock.authreportes.exception.ApiProblem;
import pe.edu.cibertec.ferrestock.authreportes.reportes.ReportModels.Table;

/** Exportacion desacoplada del SQL; entradas acotadas, sin formulas ni recursos externos. */
public final class DocumentExporter {
    public byte[] xlsx(Table table){
        check(table);
        try(var workbook=new XSSFWorkbook();var output=new ByteArrayOutputStream()){
            Sheet sheet=workbook.createSheet("Reporte");
            sheet.createRow(0).createCell(0).setCellValue(table.title());
            sheet.createRow(1).createCell(0).setCellValue(table.filter());
            var bold=workbook.createFont();bold.setBold(true);var header=workbook.createCellStyle();header.setFont(bold);
            Row headings=sheet.createRow(3);
            for(int c=0;c<table.headers().size();c++){var cell=headings.createCell(c);cell.setCellValue(table.headers().get(c));cell.setCellStyle(header);sheet.setColumnWidth(c,28*256);}
            CellStyle money=workbook.createCellStyle();money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            int index=4;
            for(var values:table.rows()){
                Row row=sheet.createRow(index++);
                for(int c=0;c<values.size();c++){
                    Object value=values.get(c);Cell cell=row.createCell(c);
                    if(value instanceof BigDecimal decimal && exactSpreadsheetNumber(decimal)){cell.setCellValue(decimal.doubleValue());cell.setCellStyle(money);}
                    else if(value instanceof Integer number){cell.setCellValue(number.doubleValue());}
                    else if(value instanceof Long number && number>=-999_999_999_999_999L && number<=999_999_999_999_999L){cell.setCellValue(number.doubleValue());}
                    else cell.setCellValue(value.toString()); // STRING incluso si inicia =, +, - o @.
                }
            }
            sheet.createFreezePane(0,4);sheet.setAutoFilter(new CellRangeAddress(3,Math.max(3,index-1),0,table.headers().size()-1));
            workbook.write(output);return output.toByteArray();
        }catch(IOException | RuntimeException ex){throw new ApiProblem(503,"EXPORTACION_NO_DISPONIBLE");}
    }
    private boolean exactSpreadsheetNumber(BigDecimal value){
        double numeric=value.doubleValue();
        return value.precision()<=15 && Double.isFinite(numeric) && BigDecimal.valueOf(numeric).compareTo(value)==0;
    }
    public byte[] pdf(Table table){
        check(table);
        try(var document=new PDDocument();var output=new ByteArrayOutputStream()){
            var font=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try(var writer=new PdfLines(document,font,table.title(),table.filter(),String.join(" | ",table.headers()))){
                for(var row:table.rows()) writer.write(String.join(" | ",row.stream().map(Object::toString).toList()));
                if(table.rows().isEmpty()) writer.write("Sin registros para los filtros indicados.");
            }
            document.save(output);return output.toByteArray();
        }catch(IOException | RuntimeException ex){throw new ApiProblem(503,"EXPORTACION_NO_DISPONIBLE");}
    }
    private void check(Table table){
        if(table==null || table.rows().size()>ReportService.MAX_ROWS || table.headers().isEmpty() || table.headers().size()>10
                || table.rows().stream().anyMatch(row -> row.size()!=table.headers().size()
                    || row.stream().anyMatch(v -> v==null || v.toString().length()>1000))) throw new ApiProblem(422,"REPORTE_INVALIDO");
    }
    private static final class PdfLines implements AutoCloseable {
        private final PDDocument document;private final PDType1Font font;private final String title,filter,headers;
        private PDPageContentStream stream;private float y;
        PdfLines(PDDocument document,PDType1Font font,String title,String filter,String headers)throws IOException{
            this.document=document;this.font=font;this.title=title;this.filter=filter;this.headers=headers;page();
        }
        private void page()throws IOException{
            if(stream!=null)stream.close();
            var page=new PDPage(new PDRectangle(PDRectangle.A4.getHeight(),PDRectangle.A4.getWidth()));document.addPage(page);
            stream=new PDPageContentStream(document,page);y=page.getMediaBox().getHeight()-35;
            line(safe(title),12);line(safe(filter),8);line(safe(headers),8);y-=5;
        }
        private String safe(String input){
            StringBuilder result=new StringBuilder();
            input.codePoints().forEach(cp -> {
                String value=new String(Character.toChars(cp));
                if(Character.isISOControl(cp)){result.append(' ');return;}
                try{font.encode(value);result.append(value);}catch(IOException | IllegalArgumentException ex){result.append('?');}
            });return result.toString();
        }
        void write(String input)throws IOException{
            String text=safe(input);List<String> lines=new ArrayList<>();StringBuilder part=new StringBuilder();
            for(int i=0;i<text.length();i++){
                part.append(text.charAt(i));
                if(font.getStringWidth(part.toString())/1000*8>760){char last=part.charAt(part.length()-1);part.setLength(part.length()-1);lines.add(part.toString());part=new StringBuilder().append(last);}
            }
            if(!part.isEmpty()) lines.add(part.toString());
            for(String row:lines){if(y<35)page();line(row,8);}
        }
        private void line(String text,int size)throws IOException{stream.beginText();stream.setFont(font,size);stream.newLineAtOffset(30,y);stream.showText(text);stream.endText();y-=size+5;}
        public void close()throws IOException{if(stream!=null)stream.close();}
    }
}
