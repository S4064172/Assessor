package assessor;

import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.codehaus.plexus.util.FileUtils;
import org.junit.Before;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.github.javaparser.ast.CompilationUnit;

import unige.assessor.AssessorTool;
import unige.assessor.TreeDecomposer;

class AssessorTestCase {
	
	boolean normalize;
	String poPrefix;
	AssessorTool tool;
	TreeDecomposer selDecomposer;

	
	private static List<File> searchFilesToAnalyze(String inputDir) {
		File dir = new File(inputDir);
		
		List<File> matchingFiles = new LinkedList<File>();
		matchingFiles.addAll(Arrays.asList(dir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String fileName) {
				return fileName.contains(".java");
			}		
		})));
		
		String[] directories = dir.list(new FilenameFilter() {
			  @Override
			  public boolean accept(File current, String name) {
			    return new File(current, name).isDirectory();
			  }
		});
		
		for (String directory : directories) {
			dir = new File(inputDir+"//"+directory);
			matchingFiles.addAll(Arrays.asList(dir.listFiles(new FilenameFilter() {
				public boolean accept(File dir, String fileName) {
					return fileName.contains(".java");
				}		
			})));
		}
		
		return matchingFiles;
	}
	
	@BeforeEach
	public void setup() {
		normalize = false;
		poPrefix = "";
		tool = new AssessorTool();
		selDecomposer =  new TreeDecomposer(normalize,poPrefix);
	}
	
	
	@ParameterizedTest
	@CsvSource({"\\case1\\file1.txt,\\case1\\file2.txt", "\\case2\\file1.txt,\\case2\\file2.txt"})
	void checkFileEqual(String fileName1, String fileName2) throws IOException {
		File file1 = new File("E:\\Universita\\Informatica Magistrale\\Terzo Anno\\Tesi\\AssessorTools\\Assessor-main\\src\\resurces\\check\\fileEqual"+fileName1);
		File file2 = new File("E:\\Universita\\Informatica Magistrale\\Terzo Anno\\Tesi\\AssessorTools\\Assessor-main\\src\\resurces\\check\\fileEqual"+fileName2);
//		assertEquals("The files differ!", FileUtils.fileRead(file1).replaceAll("^\\s+|\\s+|\\n+$", ""), FileUtils.fileRead(file2).replaceAll("^\\s+|\\s+|\\n+$", ""));
		boolean isTwoEqual = FileUtils.fileRead(file1).replaceAll("^\\s+|\\s+|\\n+$", "").equals(FileUtils.fileRead(file2).replaceAll("^\\s+|\\s+|\\n+$", ""));
		assertTrue(isTwoEqual,"File are different");
		

	}
	
	@ParameterizedTest
	@CsvSource({"\\case1\\file1.txt,\\case1\\file2.txt"})
	void checkFileNotEqual(String fileName1, String fileName2) throws IOException {
		File file1 = new File("E:\\Universita\\Informatica Magistrale\\Terzo Anno\\Tesi\\AssessorTools\\Assessor-main\\src\\resurces\\check\\fileNotEqual"+fileName1);
		File file2 = new File("E:\\Universita\\Informatica Magistrale\\Terzo Anno\\Tesi\\AssessorTools\\Assessor-main\\src\\resurces\\check\\fileNotEqual"+fileName2);
		boolean isTwoEqual = FileUtils.fileRead(file1).replaceAll("^\\s+|\\s+|\\n+$", "").equals(FileUtils.fileRead(file2).replaceAll("^\\s+|\\s+|\\n+$", ""));
		assertFalse(isTwoEqual,"File are equals");

	}
	
	@ParameterizedTest
	@ValueSource(strings = {
			"\\_CheckLogin",
			"\\_AssertNameFunction",
			"\\_AssertNameFunction_AlwaysSeparatedFunction",
			"\\_Clustering",
			"\\_MethodRefactoring",
			"\\_NameFunction_JavaStyle"})
	void test(String path) throws IOException {
		

		String inputDir = "E:\\Universita\\Informatica Magistrale\\Terzo Anno\\Tesi\\AssessorTools\\Assessor-main\\src\\resurces"+path;
		String outputDir = inputDir+"\\Output\\";
		String expexredOutputDir = inputDir+"\\OutputExpected\\";
			
		File[] matchingFiles = tool.searchFilesToAnalyze(inputDir);
		
		TreeDecomposer selDecomposer =  new TreeDecomposer(normalize,poPrefix);
		
		for(File file : matchingFiles) {
			CompilationUnit compilationUnit = tool.recoverCompilationUnit(file);			
			selDecomposer.analyzeCompilationUnit(compilationUnit);	
		}
		
		tool.writeNewClass(outputDir, selDecomposer);
		
		List<File> expectedFiles = searchFilesToAnalyze(expexredOutputDir);
		List<File> resultFiles = searchFilesToAnalyze(outputDir);
		

		assertEquals(expectedFiles.size(), resultFiles.size(), "Number of file is different");
		assertEquals(expectedFiles.toString().replace("OutputExpected",""), resultFiles.toString().replace("Output",""), "Name of file is different");
		
		for (File expectedFile : expectedFiles) {
			boolean check = false;
			for (File resultFile : resultFiles) {
				if(expectedFile.getName().equals(resultFile.getName())) {
					check = true;
					boolean isTwoEqual = FileUtils.fileRead(expectedFile).replaceAll("^\\s+|\\s+|\\n+$", "").equals(FileUtils.fileRead(resultFile).replaceAll("^\\s+|\\s+|\\n+$", ""));
					assertTrue(isTwoEqual,"File are different: " + resultFile.getName() );
				}
			}
			assertTrue(check,"Expectet file does not found");
		}
		
		
		
	}

}
