package stirling.software.SPDF.controller.api;


import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import stirling.software.SPDF.utils.WebResponseUtils;

@RestController
@Tag(name = "General", description = "General APIs")
public class MultiPageLayoutController {

	private static final Logger logger = LoggerFactory.getLogger(MultiPageLayoutController.class);

	@PostMapping(value = "/multi-page-layout", consumes = "multipart/form-data")
	@Operation(
	    summary = "Merge multiple pages of a PDF document into a single page",
	    description = "This operation takes an input PDF file and the number of pages to merge into a single sheet in the output PDF file. Input:PDF Output:PDF Type:SISO"
	)
	public ResponseEntity<byte[]> mergeMultiplePagesIntoOne(
	        @Parameter(description = "The input PDF file", required = true) @RequestParam("fileInput") MultipartFile file,
	        @Parameter(description = "The number of pages to fit onto a single sheet in the output PDF. Acceptable values are 2, 3, 4, 9, 16.", required = true, schema = @Schema(type = "integer", allowableValues = {
	                "2", "3", "4", "9", "16" })) @RequestParam("pagesPerSheet") int pagesPerSheet)
	        throws IOException {

		 if (pagesPerSheet != 2 && pagesPerSheet != 3 && pagesPerSheet != (int) Math.sqrt(pagesPerSheet) * Math.sqrt(pagesPerSheet)) {
		        throw new IllegalArgumentException("pagesPerSheet must be 2, 3 or a perfect square");
		    }

		    int cols = pagesPerSheet == 2 || pagesPerSheet == 3 ? pagesPerSheet : (int) Math.sqrt(pagesPerSheet);
		    int rows = pagesPerSheet == 2 || pagesPerSheet == 3 ? 1 : (int) Math.sqrt(pagesPerSheet);

		    PDDocument sourceDocument = PDDocument.load(file.getInputStream());
		    PDDocument newDocument = new PDDocument();
		    PDPage newPage = new PDPage(PDRectangle.A4);
		    newDocument.addPage(newPage);

		    int totalPages = sourceDocument.getNumberOfPages();
		    float cellWidth = newPage.getMediaBox().getWidth() / cols;
		    float cellHeight = newPage.getMediaBox().getHeight() / rows;

		    PDPageContentStream contentStream = new PDPageContentStream(newDocument, newPage, PDPageContentStream.AppendMode.APPEND, true, true);

		    LayerUtility layerUtility = new LayerUtility(newDocument);

		    for (int i = 0; i < totalPages; i++) {
		        PDPage sourcePage = sourceDocument.getPage(i);
		        System.out.println("Reading page " + (i+1));
		        PDRectangle rect = sourcePage.getMediaBox();
		        float scaleWidth = cellWidth / rect.getWidth();
		        float scaleHeight = cellHeight / rect.getHeight();
		        float scale = Math.min(scaleWidth, scaleHeight);
		        System.out.println("Scale for page " + (i+1) + ": " + scale);


		        int rowIndex = i / cols;
		        int colIndex = i % cols;

		        float x = colIndex * cellWidth + (cellWidth - rect.getWidth() * scale) / 2;
		        float y = newPage.getMediaBox().getHeight() - ((rowIndex + 1) * cellHeight - (cellHeight - rect.getHeight() * scale) / 2);

		        contentStream.saveGraphicsState();
		        contentStream.transform(Matrix.getTranslateInstance(x, y));
		        contentStream.transform(Matrix.getScaleInstance(scale, scale));

		        PDFormXObject formXObject = layerUtility.importPageAsForm(sourceDocument, i);
		        contentStream.drawForm(formXObject);

		        contentStream.restoreGraphicsState();
		    }


		    contentStream.close();
		    sourceDocument.close();

		    ByteArrayOutputStream baos = new ByteArrayOutputStream();
		    newDocument.save(baos);
		    newDocument.close();
	    
	    byte[] result = baos.toByteArray();
	    return WebResponseUtils.bytesToWebResponse(result, file.getOriginalFilename().replaceFirst("[.][^.]+$", "") + "_layoutChanged.pdf");
	}


}
