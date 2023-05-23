package unige.assessor;

import java.io.File;
import java.io.IOException;

import com.github.javaparser.ast.CompilationUnit;

public class AssessorMain {

	public static void main(String[] args) throws IOException{
		boolean normalize = false;
		String poPrefix = "";

		String inputDir = "E:\\Universita\\Informatica Magistrale\\Terzo Anno\\Tesi\\AssessorTools\\Assessor-main\\src\\resurces\\_AssertNameFunction_AlwaysSeparatedFunction";
		String outputDir = inputDir+"/Output/";
		
		AssessorTool tool = new AssessorTool();
		
		File[] matchingFiles = tool.searchFilesToAnalyze(inputDir);
		
		TreeDecomposer selDecomposer =  new TreeDecomposer(normalize,poPrefix);
		
		for(File file : matchingFiles) {
			CompilationUnit compilationUnit = tool.recoverCompilationUnit(file);			
			selDecomposer.analyzeCompilationUnit(compilationUnit);	
		}
		
		tool.writeNewClass(outputDir, selDecomposer);
		
		tool.writeLogs(outputDir, selDecomposer);
		System.out.println("Refactoring complete");
	}
}
