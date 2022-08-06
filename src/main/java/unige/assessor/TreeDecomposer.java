package unige.assessor;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.AssertStmt;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.VoidVisitor;

public class TreeDecomposer {
	//Delimiter generated from SeleniumIDE Extension
	private static final String DELIMITER_PO_DECLARATION = "System.out.println(\"{ASSESSOR}";
	private static final String DELIMITER_BACK_TO_MAIN = DELIMITER_PO_DECLARATION+"backToMain\");";
	private static final String DELIMITER_BACK_TO_FATHER = DELIMITER_PO_DECLARATION+"backToFather\");";
	
	
	
	//Key for share data between methods
	private static final String KEY_HASH_PO_NAME = "pageObjectName";
	private static final String KEY_HASH_PO_METHOD = "pageMethodName";
	//Standard prefix for all the PO Object
	private final String PO_PREFIX;
	//List of base Imports
	private List<ImportDeclaration> baseImports = new LinkedList<>();
	//Base Package name
	private final String basePackage = "TestCases";
	//List of all CompilationUnit (alias Java File)
	private final List<CompilationUnit> units = new LinkedList<>();
	//Main Compilation Unit
	private final CompilationUnit centralUnit;
	//Main Class for write all the Test Method
	private final ClassOrInterfaceDeclaration centralClass;
	//List of Logs
	private final List<String> logs = new LinkedList<>();
	//Normalize PO Name to lower case
	private final boolean normalize;
	
	public TreeDecomposer(boolean normalize, String poPrefix) {
		centralUnit = new CompilationUnit();	
		centralUnit.addImport("org.junit.BeforeClass");
		centralClass = createClass(centralUnit,basePackage);
		this.normalize = normalize;
		this.PO_PREFIX = poPrefix;
		_addBeforeClassStaticMethod(centralClass);		
		units.add(centralUnit);
		_addHelperClass();
	}
	
	private void _addHelperClass() {
		CompilationUnit myUtils = new CompilationUnit();	
		myUtils.addImport("java.util.function.Function");
		myUtils.addImport("org.openqa.selenium.By");
		myUtils.addImport("org.openqa.selenium.WebDriver");
		myUtils.addImport("org.openqa.selenium.support.ui.WebDriverWait");
		myUtils.setPackageDeclaration(basePackage+".PO");
		ClassOrInterfaceDeclaration clazz = myUtils.addClass("MyUtils").setPublic(true).setAbstract(true);
		MethodDeclaration method = clazz.addMethod("WaitForElementLoaded", Modifier.Keyword.PUBLIC)
				.setStatic(true).setType("void");
		method.addAndGetParameter("WebDriver", "driver");
		method.addAndGetParameter("By", "reference");
		BlockStmt block = new BlockStmt();
		block.addStatement("WebDriverWait waitFor = new WebDriverWait(driver,10);");
		block.addStatement("waitFor.until( new Function<WebDriver,Boolean>(){\n"
				+ "			public Boolean apply(WebDriver driver) {\n"
				+ "				 return driver.findElement( reference).isDisplayed();\n"
				+ "			}\n"
				+ "		});");		
		method.setBody(block);	
		units.add(myUtils);
	}

	private void _addBeforeClassStaticMethod(ClassOrInterfaceDeclaration classToAdd) {
		MethodDeclaration method = classToAdd.addMethod("setup", Modifier.Keyword.PUBLIC);
		method.setType("void").setStatic(true).addAnnotation("BeforeClass");
		BlockStmt block = new BlockStmt();
		block.addStatement("System.setProperty(\"webdriver.gecko.driver\",\"InsertGeckoPathHere\");");
		method.setBody(block);		
		
	}

	/** Returns all the compilation unit created
	 * 
	 * @return units
	 */
	public List<CompilationUnit> getUnits() {		
		return units;
	}

	
	/** Starting Point to analyze a Java File
	 * For each import in this file, if the import wasn't declared in the Main Compilation unit, this import will be added
	 * Also because the import is needed for the test method, this import will be added to the PageObject class
	 * @param unitToAnalyze
	 */
	public void analyzeCompilationUnit(CompilationUnit unitToAnalyze) {
		List<ImportDeclaration> imports = unitToAnalyze.findAll(ImportDeclaration.class);
		for(ImportDeclaration importDecl : imports) {
			if(baseImports.contains(importDecl)) continue;
			baseImports.add(importDecl);
			addImport(centralUnit,importDecl);
		}
 		//Search for all the Class declaration in the files
 		for(ClassOrInterfaceDeclaration classToAnalyze : unitToAnalyze.findAll(ClassOrInterfaceDeclaration.class))
 			analyzeClass(classToAnalyze); 		 		
 	}
	

	/** Analyze the class searching for Field Declaration, Method Declaration
	 * Because the class will return itself, the class analysis is skipped, the main operation is to analyze only Field Declaration and MethodDeclaration
	 * Other operation can cause the IllegalArgumentException with the class type that has generated
	 * @param classToAnalyze
	 */
	private void analyzeClass(ClassOrInterfaceDeclaration classToAnalyze) {
		for(BodyDeclaration<?> declaration : classToAnalyze.findAll(BodyDeclaration.class)) {
			if(declaration.isFieldDeclaration()) {				
				addFieldDeclaration(declaration.asFieldDeclaration(),centralClass);
			}else if(declaration.isMethodDeclaration()) {
				analyzeMethod(declaration.asMethodDeclaration());				
			}else if(declaration.isClassOrInterfaceDeclaration()){
				//With this i can skip the Class or Interface declaration check
			}else {			
				throw new IllegalArgumentException("Cannot analyze this class: "+ classToAnalyze.getClass());				
			}			
		}		
	}
	
	/** add the field declaration to the class, only if the field isn't already declared
	 * if the field is already declared the method won't do anything
	 * else the method will add a new member to the class
	 * 
	 * @param declaration
	 * @param classDeclaration
	 */
	private void addFieldDeclaration(FieldDeclaration declaration, ClassOrInterfaceDeclaration classDeclaration) {
		declaration.setPrivate(true);
		for(FieldDeclaration field : classDeclaration.findAll(FieldDeclaration.class))
			if(field.hashCode()==declaration.hashCode()) //Field already in
				return;
		
		classDeclaration.addMember(declaration);		
	}

	/** Analyzing the method we can have two situation, the initializer method (tearDown/setUp) or a different name for the method
	 * If it is the default initializer, this will always go to the centralClass declaration
	 * else the body is needed to be read to check for each of the instruction	 * 
	 * @param method
	 */
	private void analyzeMethod(MethodDeclaration method) {
		if("setUp".equals(method.getNameAsString()) || "tearDown".equals(method.getNameAsString())) {
			//Add the method to the central class without parameter/arguments
			addMethod(method,centralClass,null,null,null,null,false,null);			
		}else {	
			//Read the body of the statement
			Optional<BlockStmt> bodyStmt = method.findFirst(BlockStmt.class);			
			if(!bodyStmt.isPresent())
				return;
			//If the body is present then get all the annotation and create a new Method with the same name and the Public modifier
			List<AnnotationExpr> annotations = method.getAnnotations();
			MethodDeclaration newMethod = centralClass.addMethod(method.getNameAsString(), Modifier.Keyword.PUBLIC);
			for(AnnotationExpr annotation : annotations)
				newMethod.addAnnotation(annotation);
			//Then analyze all the instruction present in the body
			analyzeInstructionCalls(newMethod,bodyStmt);
		}		
	}
	
	
//	private String getFirstLocator(Optional<BlockStmt> blockStmt) {
//			String locator = "";
//			for (Node node : blockStmt.get().getChildNodes()) {
//				List<MethodCallExpr> test =  node.findAll(MethodCallExpr.class);
//				for (MethodCallExpr methodCallExpr : test) {
//					if(methodCallExpr.toString().startsWith("By") && !methodCallExpr.toString().equals(locator)) {
//						return methodCallExpr.toString();
//						
//					}
//				}
//			}
//			return locator;
//	}
	
	
	
	private void addPoDeclaretion(BlockStmt blockStmt, int pos, String POName, String methodName) {
		MethodCallExpr innerExp = new MethodCallExpr("System.out.println(\"{ASSESSOR}:"+POName+":"+methodName+"\")");
		innerExp.addArgument(new StringLiteralExpr("{ASSESSOR}:"+POName+":"+methodName));
		blockStmt.addStatement(pos,innerExp);
	}
		
	/**
	 * This function try to fill the missing PO statement. 
	 * The flow add the PO statement only if the exp has instanceof ExpressionStmt
	 * 
	 * @param blockStmt
	 * @param firstLocator
	 * @param PODeclaration
	 * @param isInner
	 * @return
	 */
	
	private boolean addMissing(BlockStmt blockStmt, String firstLocator, ClassOrInterfaceDeclaration PODeclaration, boolean isInner) {
		//TODO: gestire i comandi senza id
		boolean inner = isInner;
		int counter = 0;
		BlockStmt clone = blockStmt.clone();
		
		//if there is less than 2 locator, return false
		if(countLocator(clone.getChildNodes()) < 2)
			return false;
		
		for (Node node : clone.getChildNodes()) {
			//System.err.println(node.toString());
			if (node.toString().contains(DELIMITER_BACK_TO_MAIN) || node.toString().contains(DELIMITER_BACK_TO_FATHER) || node.toString().contains(DELIMITER_PO_DECLARATION)) {
				break;
			}
			if(node instanceof ExpressionStmt) {
				List<MethodCallExpr> test =  node.findAll(MethodCallExpr.class);
				for (MethodCallExpr methodCallExpr : test) {
					//System.err.println(methodCallExpr.toString());
					if(methodCallExpr.toString().startsWith("By") && !methodCallExpr.toString().equals(firstLocator)) {
						if(!inner) {
							String methodName = generateNameForGetterCalls(methodCallExpr);
							firstLocator = methodCallExpr.toString();
							addPoDeclaretion(blockStmt,counter,(PODeclaration==null?"AutoPo":PODeclaration.getNameAsString()),methodName);
							counter ++;
						}else
							inner = false;
						}
					}
			}
				
			counter ++;
		}
		return clone.getChildNodes().size() != blockStmt.getChildNodes().size();
	}
		
	/** Analyze each instruction recursively, to divide between TestMethod calls and PageObject calls and to create the dependency between sub PageObject call
	 *  
	 * @param methodTestSuite: name of the method under analisys
	 * @param blockStmt: contain the body of the methos
	 * @param lastPageObject: hold the last page object found
	 * @param methodToAddStatement: hold the Method where the statement is added, could be a TestSuite method or a PageObject Method
	 * @param localFieldDeclaration: found the pageObject name, and in the value the name of the variable
	 * @param values: list for each values to be called in the method call declaration
	 * @param arguments: list for each argument in the Method Declaration
	 * @param innerValues: list for each values to be called in all the inner method call declaration
	 * @param innerArguments: list for each argument in all the inner the Method Declaration
	 * @param waitForElementFound: flag is used to understand if is the first wait instruction
	 * @param isInner: flag is used to understand if is the method is inner or not
	 * @param lastLocatorUsed: is used to store the last locator 
	 */
	private void analyzeInstructionCalls_recursive(
			MethodDeclaration methodTestSuite, Optional<BlockStmt> blockStmt,
			ClassOrInterfaceDeclaration lastPageObject, MethodDeclaration methodToAddStatement,
			Map<String,String> localFieldDeclaration, 
			List<Node> values, List<NameExpr> arguments,
			List<Node> innerValues, List<NameExpr> innerArguments,
			boolean waitForElementFound, boolean isInner,
			String lastLocatorUsed
			) {
		
			if( blockStmt.get().getStatements().size() == 0) {
				if (lastPageObject != null)
					addPageObjectCall(methodTestSuite, lastPageObject, methodToAddStatement, localFieldDeclaration.get(lastPageObject.getNameAsString()),values,arguments,innerValues, innerArguments, isInner);
				return;
			}
				
		
			Node node = blockStmt.get().getStatement(0);
			
			Map<String,String> delimiterFound = checkDelimiterInstruction(node);
			
			boolean delimiterEnd = false;
			
			if(delimiterFound==null) {
				delimiterEnd = _checkDelimiterEnd(node);					
			}
				
			//If we are parsing an inner method and we found an new startDelimiter or a BacktoMain
			//we add manually the delimiter backToFather
			if((delimiterFound!=null || delimiterEnd) && lastPageObject!=null && isInner) {
				blockStmt.get().addStatement(0, new NameExpr(DELIMITER_BACK_TO_FATHER.replace(";", "")));
				analyzeInstructionCalls_recursive(methodTestSuite, blockStmt, lastPageObject, methodToAddStatement, localFieldDeclaration, values,arguments, innerValues, innerArguments, waitForElementFound, true,lastLocatorUsed);
				return;
			}
			
			//If we are changing the PO page but we forgot the backToMain, we add it
			//otherwise the tool instantiates the new object in the wrong place
			if(delimiterFound != null && lastPageObject != null && !delimiterFound.get("pageObjectName").toString().equals(lastPageObject.getName().toString())) {
				blockStmt.get().addStatement(0, new NameExpr(DELIMITER_BACK_TO_MAIN.replace(";", "")));
				analyzeInstructionCalls_recursive(methodTestSuite, blockStmt, lastPageObject, methodToAddStatement, localFieldDeclaration, values,arguments, innerValues, innerArguments, waitForElementFound, isInner,lastLocatorUsed);
				return;
			}
			
		
			if(delimiterFound==null && !delimiterEnd) 
				delimiterEnd =  _checkDelimiterChildEnd(node);
			
				
			if(delimiterFound == null && !delimiterEnd && addMissing(blockStmt.get(),"",lastPageObject,isInner) && lastPageObject != null) {
				analyzeInstructionCalls_recursive(methodTestSuite, blockStmt, lastPageObject, methodToAddStatement, localFieldDeclaration,values,arguments, innerValues, innerArguments, waitForElementFound, isInner,lastLocatorUsed);
				return;
			}
								
			//If there is a delimiter
			if(delimiterFound!=null || delimiterEnd ) {
				//if a pageObject is found, and there isn't the and delimiter
				//starts the inner generation
				if(lastPageObject!=null && !delimiterEnd) {
											
					analyzeInstructionCalls_recursive(methodToAddStatement, blockStmt, null, methodToAddStatement, localFieldDeclaration,values,arguments, innerValues, innerArguments, waitForElementFound, true,lastLocatorUsed);
					
					innerValues.addAll(values);
					innerArguments.addAll(arguments);
					isInner = false;
//					//TODO: capire questo che serve
//					analyzeInstructionCalls_recursive(methodTestSuite, blockStmt, lastPageObject, methodToAddStatement, localFieldDeclaration, values,arguments, innerValues, innerArguments, waitForElementFound, false,lastLocatorUsed);
//					
//					lastPageObject = null;
//					lastLocatorUsed = "";
//					innerValues = new LinkedList<>();
//					innerArguments = new LinkedList<NameExpr>();
					
					
				}else {
					//if the instruction is to go back to the write to the main Test Method
					//clear the lastPageObject, the valuesInner, the innerArguments
					//and change the method where to add statement
					if(delimiterEnd) {
						//if lastPageObject is null means that I have
						//already added the page object
						blockStmt.get().remove(node);
						
						
						if(lastPageObject != null) {
							addPageObjectCall(methodTestSuite, lastPageObject, methodToAddStatement, localFieldDeclaration.get(lastPageObject.getNameAsString()),values,arguments,innerValues, innerArguments, isInner);	
							
							lastPageObject = null;
							methodToAddStatement = methodTestSuite;
							lastLocatorUsed = "";
							innerValues = new LinkedList<>();
							innerArguments = new LinkedList<NameExpr>();
							isInner = false;
							
						}else {
							analyzeInstructionCalls_recursive(methodTestSuite, blockStmt, lastPageObject, methodToAddStatement, localFieldDeclaration, values,arguments, innerValues, innerArguments, waitForElementFound, isInner,lastLocatorUsed);
						}
						return;
					} else {
						waitForElementFound = false;
						//If a delimiter is found, get the pageObject Name
						String pageObjectName = delimiterFound.get(KEY_HASH_PO_NAME);
						//recover the pageObject from the class
						lastPageObject = getPageObject(pageObjectName);
						//if the pageObject is not found, then should be created
						if(lastPageObject==null) 
							lastPageObject = createPageObject(pageObjectName);
						//if the local variable inside the TestMethod isn't declared, then the declaration must be added
						if(!localFieldDeclaration.containsKey(pageObjectName)) {
							//The value represent the Variable name, using an _ to be better identified inside the code 
							localFieldDeclaration.put(pageObjectName, "_"+pageObjectName);
							//The initialization of the PageObject is always formed by the 3 variables driver,var,js 
							methodTestSuite.getBody().get()
								.addStatement(pageObjectName+ " "+localFieldDeclaration.get(pageObjectName) +" = new "+pageObjectName+"(driver,js,vars);");
						}
						//Now a new void method is created with the PageMethodName.
						//if the method will need a different return statement, this will be change in a second time
						methodToAddStatement = new MethodDeclaration() 
							.setType("void")
							.setName(delimiterFound.get(KEY_HASH_PO_METHOD))
							.setPublic(true);
						blockStmt.get().remove(node);
					}
					
				}
				analyzeInstructionCalls_recursive(methodTestSuite, blockStmt, lastPageObject, methodToAddStatement, localFieldDeclaration,values,arguments, innerValues, innerArguments, waitForElementFound,isInner,lastLocatorUsed);

				return;
				
			}
			//Because each node is linked with others node, if the node isn't cloned the program will crash because the structure that holds the node is not modifiable
			Node clonedNode = node.clone();
			
			BlockStmt bodyMethod = methodToAddStatement.getBody().get();
			if(clonedNode instanceof ExpressionStmt) { //2 option, is Assert or normal command
								
				if( lastPageObject!=null && checkLocator(clonedNode,lastLocatorUsed) && !clonedNode.toString().contains("assert") ) {
					//create Wait for element to prevent missing loading on async loading
					lastLocatorUsed = createWaitForElement((ExpressionStmt) clonedNode,bodyMethod,waitForElementFound);
					waitForElementFound = true;
				}
				
				if(clonedNode.toString().contains("sendKeys")) {
					String clearCommand = createClearCommandBeforeSendKeys((ExpressionStmt)clonedNode);
					bodyMethod.addStatement(clearCommand);		
				}
				
				ExpressionStmt expStmt = (ExpressionStmt)clonedNode;
				//if the pageObject isn't already found, then there is no need to check for arguments
				if(lastPageObject!=null)  				
					analyzeMethodArguments(expStmt,values,arguments);			
				//If the statement doesn't start with assert means that it is a normal call, also the pageObject needed to be inizialited
				if(expStmt.toString().startsWith("assert") && lastPageObject!=null) {
				
					//Add the previews call method, that will return void
//					addPageObjectCall(methodTestSuite, lastPageObject, methodToAddStatement, localFieldDeclaration.get(lastPageObject.getNameAsString()),values,arguments, innerValues, innerArguments,isInner);	
					
					//if there is an assert inside a method with other selenium command
					//the tool create a new method only for the assert
					if(isInner) {
						String methodName = null;
						List<MethodCallExpr> methodCallExprList =  clonedNode.findAll(MethodCallExpr.class);
						for (MethodCallExpr methodCallExpr : methodCallExprList) {
							//System.err.println(methodCallExpr.toString());
							if(methodCallExpr.toString().startsWith("By")) {
								methodName = generateNameForGetterCalls(methodCallExpr);
							}
						}
						MethodCallExpr innerExp = new MethodCallExpr("System.out.println(\"{ASSESSOR}:"+lastPageObject.getNameAsString()+":"+methodName+")");
						innerExp.addArgument(new StringLiteralExpr("{ASSESSOR}:"+lastPageObject.getNameAsString()+":"+methodName));
						blockStmt.get().addStatement(0,innerExp);
						blockStmt.get().addStatement(0, new NameExpr(DELIMITER_BACK_TO_MAIN.replace(";", "")));
						analyzeInstructionCalls_recursive(methodTestSuite, blockStmt, lastPageObject, methodToAddStatement, localFieldDeclaration, values,arguments, innerValues, innerArguments, waitForElementFound, isInner ,lastLocatorUsed);
						return;
					}
					
					//if the method contains a search for an element, then create the statement
					if(expStmt.toString().contains("driver.findElement")) { 						
						analyzeAssertCallExpStmt(
								methodTestSuite, 
								lastPageObject,
								localFieldDeclaration.get(lastPageObject.getNameAsString()), 		
								expStmt,
								methodToAddStatement.getNameAsString(),
								lastLocatorUsed);
					}else { //nothing special with this assert
						bodyMethod = methodTestSuite.getBody().get();
						bodyMethod.addStatement(expStmt);	
					}	
					//Return to write in the main statement as default
					lastPageObject = null;
					lastLocatorUsed = "";
					isInner = false;
					innerValues = new LinkedList<>();
					innerArguments = new LinkedList<NameExpr>();
					methodToAddStatement = methodTestSuite;
				}
				else { 
					//if it's not a special statement (assert), just add it to the bodyMethod of the TestMethod or PageObject Method
					bodyMethod.addStatement(expStmt);	
				}
			}else if (clonedNode instanceof BlockStmt) { //2 option, can contain Assert or normal block
				BlockStmt blockInstruction = (BlockStmt) clonedNode;				
				if(lastPageObject==null) { //No pageObject, so i don't care what contains or there isn't an assert call
					bodyMethod.addStatement(blockInstruction);
				} else if(searchForAssertInBlockStmt(blockInstruction)) {
					//Add the previews call method, that will return void
//					addPageObjectCall(methodTestSuite, lastPageObject, methodToAddStatement, localFieldDeclaration.get(lastPageObject.getNameAsString()),values,arguments, innerValues, innerArguments,isInner);	
					
					//if there is an assert inside a method with other selenium command
					//the tool create a new method only for the assert
					if(isInner) {
						String methodName = null;
						List<MethodCallExpr> methodCallExprList =  clonedNode.findAll(MethodCallExpr.class);
						for (MethodCallExpr methodCallExpr : methodCallExprList) {
							//System.err.println(methodCallExpr.toString());
							if(methodCallExpr.toString().startsWith("By")) {
								methodName = generateNameForGetterCalls(methodCallExpr);
							}
						}
						MethodCallExpr innerExp = new MethodCallExpr("System.out.println(\"{ASSESSOR}:"+lastPageObject.getNameAsString()+":"+methodName+")");
						innerExp.addArgument(new StringLiteralExpr("{ASSESSOR}:"+lastPageObject.getNameAsString()+":"+methodName));
						blockStmt.get().addStatement(0,innerExp);
						blockStmt.get().addStatement(0, new NameExpr(DELIMITER_BACK_TO_MAIN.replace(";", "")));
						analyzeInstructionCalls_recursive(methodTestSuite, blockStmt, lastPageObject, methodToAddStatement, localFieldDeclaration, values,arguments, innerValues, innerArguments, waitForElementFound, isInner ,lastLocatorUsed);
						return;
					}
					
					List<Node> childInstruction = blockInstruction.getChildNodes();
					
					//if the method contains a search for an element, then create the statement, it should never be empty because contains at least 1 assert call
					if(childInstruction.get(0).toString().contains("driver.findElement")) { 
							generateAssertCallBlockStmt(methodTestSuite,lastPageObject, localFieldDeclaration.get(lastPageObject.getNameAsString()), 
									 blockInstruction,values,arguments,methodToAddStatement.getNameAsString(),lastLocatorUsed);					
					}else { //nothing special with this assert
						bodyMethod = methodTestSuite.getBody().get();
						bodyMethod.addStatement(blockInstruction);	
					}	
					lastPageObject = null;
					lastLocatorUsed = "";
					isInner = false;
					methodToAddStatement = methodTestSuite;
					innerValues = new LinkedList<>();
					innerArguments = new LinkedList<NameExpr>();
				}else {					
					BlockStmt blockParsed = new BlockStmt();
					bodyMethod.addStatement(blockParsed);
					for(Node child : blockInstruction.getChildNodes()) {
						ExpressionStmt expStmt = (ExpressionStmt)child.clone();
						analyzeMethodArguments(expStmt,values,arguments);
						if (checkLocator(child,lastLocatorUsed)) {
							lastLocatorUsed = createWaitForElement((ExpressionStmt) expStmt,blockParsed,waitForElementFound);
							waitForElementFound = true;
						}
							
						blockParsed.addStatement(expStmt);
					}					
				}
			}else if (clonedNode instanceof Comment) {
				bodyMethod.addOrphanComment((Comment) clonedNode);
			}else { 
				bodyMethod.addStatement((Statement) clonedNode);
			}
			blockStmt.get().remove(node);

			analyzeInstructionCalls_recursive(methodTestSuite, blockStmt, lastPageObject, methodToAddStatement, localFieldDeclaration, values,arguments, innerValues, innerArguments, waitForElementFound,isInner,lastLocatorUsed);
			lastPageObject = null;
			methodToAddStatement = methodTestSuite;
			lastLocatorUsed = "";
			innerValues = new LinkedList<>();
			innerArguments = new LinkedList<NameExpr>();
			isInner = false;
			return;
	}
		
	/** Analyze each instruction, to divide between TestMethod calls and PageObject calls
	 *  
	 * @param methodTestSuite: name of the method under analisys
	 * @param blockStmt: contain the body of the methos
	 */
	private void analyzeInstructionCalls(MethodDeclaration methodTestSuite, Optional<BlockStmt> blockStmt) {
		
		//This will hold the last page object found
		ClassOrInterfaceDeclaration lastPageObject = null;
		//This will hold the Method where the statement is added, could be a TestSuite method or a PageObject Method
		MethodDeclaration methodToAddStatement = methodTestSuite;
		//In the Key we can found the pageObject name, and in the value the name of the variable
		Map<String,String> localFieldDeclaration = new HashMap<>();
		//List for each values to be called in the method call declaration
		List<Node> values = new LinkedList<>();
		//List for each argument in the Method Declaration
		List<NameExpr> arguments = new LinkedList<NameExpr>();
		//List for each values to be called in all the inner method call declaration
		List<Node> innerValues = new LinkedList<>();
		//List for each argument in all the inner the Method Declaration
		List<NameExpr> innerArguments = new LinkedList<NameExpr>();
		
		boolean waitForElementFound = false;
		boolean isInner = false;
		String lastLocatorUsed = "";
						
		while(blockStmt.get().getStatements().size() > 0) {
			analyzeInstructionCalls_recursive(methodTestSuite, blockStmt, lastPageObject, methodToAddStatement, localFieldDeclaration, values, arguments,innerValues,innerArguments, waitForElementFound,isInner,lastLocatorUsed);
			lastPageObject = null;
			lastLocatorUsed = "";
			innerValues = new LinkedList<>();
			innerArguments = new LinkedList<NameExpr>();
		}
			

	}

	private boolean checkLocatorIsPresent(Node node) {
		return node.toString().contains("By");
	}
	
	private boolean checkChangeLocator(Node node, String lastLocator) {
		return !node.toString().contains(lastLocator) || lastLocator.equals("");
	}
	
	/* 
	 * This function is used to check if the wait command must be added
	 * The checks are:
	 * 1) check i the locator is changed
	 * 2) check if the locator is present
	 * 
	 *  @param: node
	 *  @param: lastLocator
	 *  
	 */
	
	private boolean checkLocator(Node node, String lastLocator) {
		return checkChangeLocator(node, lastLocator) && checkLocatorIsPresent(node);
	}
	
		
	private MethodCallExpr findByInstruction(List<Node> exp) {
		//ex: elements = driver.findElements(By.linkText("Sign in"));
		//ex: dropdown.findElement(By.xpath("//option[. = 'Class1']")).click()
		//ex: vars.put("before", driver.findElement(By.cssSelector(".hidden-xs > .text-primary")).getText());
		if (exp.get(0).toString().split("findElement")[0].contains("="))
			exp = exp.get(0).getChildNodes();
		
		for (Node node : exp) {
			if( checkLocatorIsPresent(node) )
				return (MethodCallExpr) node;
		}
		return null;
	}
	
	private Expression findArgument(MethodCallExpr methodCall) {
		MethodCallExpr clone = methodCall;
				
		while (clone.getArguments().size() == 0) {
			clone = (MethodCallExpr) clone.getChildNodes().get(0);
		}
		
		return  clone.getArgument(0);
	}
	
	private String createWaitForElement(ExpressionStmt instruction, BlockStmt bodyMethod, boolean waitForElementFound) {
		Node cloned = instruction.clone().getChildNodes().get(0);
		MethodCallExpr methodCall;
		Expression argument;
//		if(cloned.toString().contains("assert")) {
//			System.err.println(findByInstruction(cloned));
//			methodCall = (MethodCallExpr) cloned.getChildNodes().get(1).getChildNodes().get(0);	
//		}else {
//			if (cloned.toString().split("findElement")[0].contains("=")){
//				methodCall = (MethodCallExpr) cloned.getChildNodes().get(0).getChildNodes().get(2);
//			}else {
//				methodCall = findByInstruction(cloned);
//			}
//		}	
		methodCall = findByInstruction(cloned.getChildNodes());
		argument = findArgument(methodCall);
		if(!waitForElementFound)
			bodyMethod.addStatement("By elem = " + argument.toString() + ";");
		else 
			bodyMethod.addStatement("elem = " + argument.toString() + ";");
		bodyMethod.addStatement("MyUtils.WaitForElementLoaded(driver,elem);");
		return argument.toString();
	}

	private String createClearCommandBeforeSendKeys(ExpressionStmt instruction) {
		Node clonedClear = instruction.clone().getChildNodes().get(0);
		String command = "";
		for(Node child : clonedClear.getChildNodes()) {
			if(child.toString().contains("sendKeys")) {
				command+="clear();";
				break;
			}else {
				command+=child.toString()+".";
			}
		}
		return command;
	}


	 /** Search in all the command of the BlockStmt if there is a assert call
	 * return true if the assert call exist else false
	 * @param blockInstruction
	 * @return
	 */
	private boolean searchForAssertInBlockStmt(BlockStmt blockInstruction) {	
		for(Node child : blockInstruction.getChildNodes()) 
			if(child.toString().startsWith("assert")) 
				return true;					
		return false;
	}

	/** Add the pageObject Calls to the Test Method
	 * 
	 * @param methodTestSuite
	 * @param pageObject
	 * @param methodPO
	 * @param pageObjectVariable
	 * @param values
	 * @param argument
	 * @param innerValues: list for each values to be called in all the inner method call declaration
	 * @param innerArguments: list for each argument in all the inner the Method Declaration
	 */
	private void addPageObjectCall(
			MethodDeclaration methodTestSuite, ClassOrInterfaceDeclaration pageObject,
			MethodDeclaration methodPO, String pageObjectVariable,
			List<Node> values, List<NameExpr> arguments,
			List<Node> innerValues, List<NameExpr> innerArguments,
			boolean isInner) {	
		if(methodPO.getBody().get().getChildNodes().size()==0) { //No instruction found
			addWarning("The method declaration " +methodPO.getNameAsString() +" in the pageObject: "+pageObject.getNameAsString() + " is empty. So it will be discharged" );			
		}else {
			if(isInner) {
				MethodDeclaration methodAdded = addMethod( methodPO,  pageObject,values,arguments,innerValues, innerArguments,isInner,methodTestSuite);
				addCallToMethod("",methodTestSuite,methodAdded,values,arguments, innerValues, innerArguments,isInner);
			} else {
				MethodDeclaration methodAdded = addMethod( methodPO,  pageObject,values,arguments,null, null,isInner,methodTestSuite);
				addCallToMethod(pageObjectVariable,methodTestSuite,methodAdded,values,arguments, null, null,isInner);
			}
							
		}
		if(!isInner) {
			values.clear();
			arguments.clear();
		}
			
	}


	/** Search in all the Compilation unit if the pageObject is already declared
	 * if it is already declared will return the pageObject class 
	 * else null
	 * 
	 * @param pageObject
	 * @return
	 */
	private ClassOrInterfaceDeclaration getPageObject(String pageObject) {
		for(CompilationUnit unit : units)
			for(ClassOrInterfaceDeclaration searchedClass : unit.findAll(ClassOrInterfaceDeclaration.class)) 		
				if(searchedClass.getNameAsString().equals(pageObject))
					return searchedClass;		
		return null;
	}
	
	/** Create a new Page Object Class with the default initialization
	 * This method will also create a new CompilationUnit that will be added to the list of all the compilation unit
	 * 
	 * @param pageObject
	 * @return
	 */	
	private ClassOrInterfaceDeclaration createPageObject(String pageObject) {
		Map<String,String> hashMap = new HashMap<>();
		hashMap.put("WebDriver", "driver");
		hashMap.put("Map<String,Object>", "vars");
		hashMap.put("JavascriptExecutor", "js");		
		CompilationUnit unit = new CompilationUnit();
		units.add(unit);
		addImports(unit,baseImports);		
		ClassOrInterfaceDeclaration classCreated = createClass(unit,pageObject);
				
		for(Map.Entry<String,String> entry : hashMap.entrySet()) {
			classCreated.addField(entry.getKey(), entry.getValue());			
		}		
		
		ConstructorDeclaration constructor = classCreated.addConstructor().setPublic(true);
		BlockStmt blockStmt = new BlockStmt();
		
		for(Map.Entry<String,String> entry : hashMap.entrySet()) {			
			constructor.addParameter(entry.getKey(), entry.getValue());
			blockStmt.addStatement("this."+entry.getValue()+"="+entry.getValue()+";");
		}		
		constructor.setBody(blockStmt);
		return classCreated;
	}
	
	/** Create a MethodCallDeclaration with the list of values 
	 * 
	 * @param variableName
	 * @param methodWhoCalls
	 * @param methodToCall
	 * @param values
	 * @param innerValues: list for each values to be called in all the inner method call declaration
	 * @param innerArguments: list for each argument in all the inner the Method Declaration
	 */	
	private void addCallToMethod(String variableName, MethodDeclaration methodWhoCalls, MethodDeclaration methodToCall,
			List<Node> values,List<NameExpr> arguments, 
			List<Node> innerValues, List<NameExpr> innerArguments,
			boolean isInner) {
		String statement = variableName;
		if(isInner) {
			statement+= methodToCall.getNameAsString()+"(";
			statement+= argumentParser(arguments,innerArguments);
		}else {
			statement+= "."+methodToCall.getNameAsString()+"(";
			statement+= argumentParser(values,innerValues);
		}
		statement+=");";		
		methodWhoCalls.getBody().get().addStatement(statement);		
	}


	/* Utility Method for JavaParser */

 	/**Add package to a Compilation unit
 	 * If the package is not the CentralUnit, then add it's reference to the central unit 
 	 * as import
 	 * 
 	 * @param unit
 	 * @param packageName 	
 	 */
 	private void addPackage(CompilationUnit unit, String packageName) {
		unit.setPackageDeclaration(packageName);
		/* This will add the import only if it's not the CentralUnit, but because there should be a bug, it won't add the Import after the class declaration*/
		if(unit!=this.centralUnit) 	{				
			addImport(this.centralUnit, new	ImportDeclaration(packageName,false,true));	
		}
		
	}
	
	/**Add multiple import to a compilation unit
	 * 
	 * @param unit
	 * @param importElements
	 */
 	private void addImports(CompilationUnit unit, List<ImportDeclaration> importElements) {
		for(ImportDeclaration importElement : importElements)
			addImport(unit,importElement);	
	}
 	
	/**Add a single import to a compilation unit
	 * 
	 * @param unit
	 * @param importEl
	 */
 	private void addImport(CompilationUnit unit,ImportDeclaration importEl){
		unit.addImport(importEl);
	}
 	
	/** The method will create a new Class if and only if there isn't a Class with the same name in the entire list of compilation unit
	 * If the class doesn't exist the class is created and a Package is assigned and all the imports that is present in the TestCases
	 * If the class already exist in the CompilationUnit list, the class object is returned
	 * 	
	 * @param unit
	 * @param className
	 * @return 
	 */
 	private ClassOrInterfaceDeclaration createClass(CompilationUnit unit,String className) {
		for(ClassOrInterfaceDeclaration searchedClass : unit.findAll(ClassOrInterfaceDeclaration.class)) 		
			if(searchedClass.getNameAsString().equals(className))
				return searchedClass;
		
		ClassOrInterfaceDeclaration newClass = unit.addClass(className).setPublic(true);
		boolean isNotCentralPackage = centralUnit!=unit;
		String packageName = basePackage;
		if(isNotCentralPackage) {
			packageName+=".PO";			
		}
		addPackage(unit,packageName);
		return newClass;
	}	
 	
 	
 	/** The method is modified and the list of parameters is added in the declaration
 	 * If the method already exist in the class, then the method is returned
 	 * else the method is added to the method list of the class. If there is another method with the same name but different body
 	 * then the method is renamed with a progressive from 1 to N
 	 * 
 	 * @param methodToAdd
 	 * @param addToClass
 	 * @param argTypes
 	 * @param argName
 	 * @return
 	 */
	private MethodDeclaration addMethod(MethodDeclaration methodToAdd, ClassOrInterfaceDeclaration addToClass,
			List<Node> argTypes, List<NameExpr> argName,
			List<Node> argTypesInner, List<NameExpr> argNameInner,
			boolean isInner,
			MethodDeclaration methodTestSuite) {
		methodAddArguments(methodToAdd,argTypes,argName,argTypesInner,argNameInner);
		MethodDeclaration alreadyInMethod = getMethodAlreadyIn(methodToAdd,addToClass);
				
		if(alreadyInMethod!=null)
			return alreadyInMethod;
		
		alreadyInMethod = getClusteredMethod(methodToAdd,addToClass,argTypes,argName,methodTestSuite);
		
		if(alreadyInMethod!=null)
			return alreadyInMethod;

		
		
		int index = 1;
		String baseMethodName = methodToAdd.getNameAsString();
		while(methodSameName(methodToAdd,addToClass)) {
			methodToAdd.setName(baseMethodName+"_"+index);
			index++;
		}
		if(index>1) {
			addWarning("Method name duplicate in PO: "+addToClass.getNameAsString()+" the method " + baseMethodName+" is renamed in  "+methodToAdd.getNameAsString());
		}
		addToClass.addMember(methodToAdd);	
		return methodToAdd;
	}
	
	/** If there is a list of argTypes, then the method is modified and for each argTypes a new Parameter is added
	 * because all the parameters is of types String, this is hard-coded. But the Node with he real values can be read in the argTypes list
	 * 
	 * @param method
	 * @param argTypes
	 * @param argName
	 */
	private void methodAddArguments(MethodDeclaration method, List<Node> argTypes,
			List<NameExpr> argName, List<Node> argTypesInner, 
			List<NameExpr> argNameInner) {
		if(argTypes==null) return;
		for(int i=0;i<argTypes.size();i++) {
			//Node value = argTypes.get(i); //if i want to parse other type of Input
			if(argTypesInner == null || !contains(argNameInner, argName.get(i).toString())) {
				NameExpr nameVariable = argName.get(i);
				Parameter parameter = new Parameter();
				parameter.setType("String");		
				parameter.setName(nameVariable.toString());
				method.addParameter(parameter);
			}
			
		}
	}

	/** Search if the method is already in the class.
	 * If the method already exist and have the same number of parameter, name, body the method already created is returned
	 * if the method has the same list of parameter and body, but with a different name, a warning is added to the log, and the method is returned
	 * Else null is returned that indicates the method is new and should be added to the class
	 * @param methodToSearch
	 * @param classToSearch
	 * @return
	 */
	private MethodDeclaration getMethodAlreadyIn(MethodDeclaration methodToSearch, ClassOrInterfaceDeclaration classToSearch) {
		List<MethodDeclaration> methods = classToSearch.findAll(MethodDeclaration.class);
//		System.err.println(methodToSearch.getNameAsString());
		for(MethodDeclaration method : methods) { 
			if(method.hashCode()==methodToSearch.hashCode()) 				
				return method;			
				
			
			if(method.getParameters().size() != methodToSearch.getParameters().size()) 			
				continue;
			
			String bodyStatement = method.getBody().get().toString().replaceAll("key[1-9]*", "");
			
			if(bodyStatement.equals(methodToSearch.getBody().get().toString().replaceAll("key[1-9]*", ""))) {	
				if(!method.getNameAsString().equals(methodToSearch.getNameAsString())) {
					String unified = method.getNameAsString();
					addWarning("For PO:" +classToSearch.getNameAsString()+" method "+methodToSearch.getNameAsString() +" and "+unified+" unified under the name "+unified+" since bodies and paramters list are identical");
				}
				return method;
			}			
		}
		return null;
	}
	
	/*
	 *This method is used to find the position of the statement and infer the position of the argument
	 *If the statement has no argument and it is present in the statements list the method return -2 
	 *If the statement has same arguments and it is present in the statements list the method return the position of the statement
	 *If the statement is absent the method return -1
	 *
	 *@param statementToSearch: method that we want to add
	 *@param statements: list of methods already in the cluster
	 *@return pos: the position of the argument
	 */
	private int containsStatement(Statement statementToSearch, List<Statement> statements) {
		String stm = statementToSearch.toString().replaceAll("key[1-9]*", "");
		int pos = 0;
		for (Statement statement : statements) {
			Statement analize = statement;
			if(statement instanceof IfStmt)
				analize = ((IfStmt) statement).getThenStmt();
			
			//remove the arguments and check id the methods are the same
			if(analize.toString().replaceAll("key[1-9]*", "").equals(stm))
				//only if the method has an argument the method return the pos
				
				if (analize.toString().contains("key"))
					return pos;
				else 
					return -2;
			
			//only if the method has an argument we increment the counter
			if(analize.toString().contains("key"))
				pos++;
		}
		return -1;
	}
	
	/**
	 * This function count how many locators are present in the given list
	 * 
	 * @param nodeList
	 * @return
	 */
	private int countLocator(List<Node> nodeList) {
		int countLocator = 0;
		String locator = "";
		for (Node node : nodeList) {
			
			if (node.toString().contains(DELIMITER_BACK_TO_MAIN) || node.toString().contains(DELIMITER_BACK_TO_FATHER) || node.toString().contains(DELIMITER_PO_DECLARATION))
				break;
			
			
			List<MethodCallExpr> expsList =  node.findAll(MethodCallExpr.class);
			for (MethodCallExpr methodCallExpr : expsList) {
				if(methodCallExpr.toString().startsWith("By") && !methodCallExpr.toString().equals(locator)) {
					countLocator ++;
					locator = methodCallExpr.toString();
				}
			}
			
		}
		return countLocator;
	}
	
	/**This function is used to cluster the PO-method that have the same name
	 * Once we found a po-method with the same name, we union the two po-method and 
	 * reorganize the parameters of the two methods
	 * 
	 * @param methodToSearch 
	 * @param classToSearch
	 * @param argTypes
	 * @param argName
	 * @param methodTestSuite
	 * @return clusteredMethod
	 */
	private MethodDeclaration getClusteredMethod(
			MethodDeclaration methodToSearch,
			ClassOrInterfaceDeclaration classToSearch,
			List<Node> argTypes, List<NameExpr> argName,
			MethodDeclaration methodTestSuite
			) {
		MethodDeclaration clusteredMethod = null;
		
		//Search a method in the same class with the same name
		List<MethodDeclaration> methods = classToSearch.findAll(MethodDeclaration.class);
		for(MethodDeclaration method : methods) { 
			if (method.getNameAsString().equals(methodToSearch.getNameAsString())) {
				clusteredMethod = method;
				break;
			}
		}
		
		if (clusteredMethod == null)
			return null;
			
		//is used to store the new parameters order
		List<Node> newArgs = new LinkedList<Node>();
		for (Parameter param : clusteredMethod.getParameters()) {
			newArgs.add(new NameExpr("null"));
		}
		
		//Perform the cluster between the method
		int clusteredMethodParamiters = clusteredMethod.getParameters().size();
		//used to update the name of the parameters
		int addedParams = 1;
		
		int index = 0;
		
		//If the methods did not have at least one parameter we can not cluster them
		if (clusteredMethodParamiters == 0 || methodToSearch.getParameters().size() == 0)
			return null;
		
		//if the methods contains some locator  we can not cluster them
		if (countLocator(methodToSearch.getChildNodes()) > 0 || countLocator(clusteredMethod.getChildNodes()) > 0)
			return null;
		//System.err.println("classToSearch " + classToSearch.getNameAsString());
		//System.err.println("clusteredMethod " + clusteredMethod.getNameAsString());
		//System.err.println("methodToSearch " + methodToSearch.getNameAsString());
		for (Statement statement : methodToSearch.getBody().get().getStatements()) {
			//System.err.println("\tstatement " + statement);
			//If there is a common statement I have to update only the newArgs list
			int pos = containsStatement(statement,clusteredMethod.getBody().get().getStatements());
			//System.err.println("\tpos " + pos);
			
			if(pos == -2) continue;
			
			if(pos == -1) {
				
				List<NameExpr> expr = statement.findAll(NameExpr.class);
				for (NameExpr exp : expr) {
					Parameter result = null;
					//Retrieve the type of the new argument
					for (Parameter param : methodToSearch.getParameters()) {
						if (param.toString().endsWith(exp.toString()))
							result = param;
					}
					
					//change the name of the parameter & argument
					exp.setName(new SimpleName("key"+(clusteredMethodParamiters + addedParams)));
					result.setName(new SimpleName("key"+(clusteredMethodParamiters + addedParams)));
					
					clusteredMethod.addParameter(result);
					newArgs.add(argTypes.get(index));
					
					addedParams ++;
					index++;
				}
				clusteredMethod.getBody().get().addStatement(statement);
				
			}else {
				
				List<NameExpr> expr = statement.findAll(NameExpr.class);
				//System.err.println("\texpr " + expr);
				for (NameExpr exp : expr) {
					newArgs.set(pos, argTypes.get(index));
					index++;
				}
				
			}
			
		}
		
		//Update the argsType list
		for(int i = 0; i < newArgs.size(); i++) {
			if(i<argTypes.size())
				argTypes.set(i, newArgs.get(i));
			else
				argTypes.add(newArgs.get(i));
		}
		
		
		
		MethodDeclaration clone = clusteredMethod.clone();
		List<Node> list = clone.getBody().get().getChildNodes();
		int stmIndex = 0;
		int argsIndex=1;
		for (Statement node : clone.getBody().get().getStatements()) {
//			System.err.println(node);
			if ( !(node instanceof IfStmt) &&  node.toString().contains("(key")) {
				
				Expression nullCheck = new BinaryExpr(new NameExpr("key"+(argsIndex++)), new NullLiteralExpr(), BinaryExpr.Operator.NOT_EQUALS);
				IfStmt ifstm = new IfStmt(nullCheck , node, null);
				clusteredMethod.getBody().get().setStatement(stmIndex, ifstm);				
			}
			
			if(node instanceof IfStmt)
				argsIndex ++;
			stmIndex++;
			
			
		}
		
		
		//Update the usage of the cluster method in all the tests
		List<MethodCallExpr> methodList = methodTestSuite.getParentNode().get().findAll(MethodCallExpr.class);
		//System.err.println("methodToSearch " + classToSearch.getNameAsString()+"."+methodToSearch.getNameAsString());
		//System.err.println("classToSearch " + classToSearch.getNameAsString());
		for (MethodCallExpr methodCallExpr : methodList) {
			if (methodCallExpr.toString().contains(classToSearch.getNameAsString()+"."+methodToSearch.getNameAsString())) {
				//System.err.println("methodCallExpr " + methodCallExpr);
				int counter = clusteredMethodParamiters;
				while (counter < clusteredMethod.getParameters().size()) {
					methodCallExpr.addArgument(new NameExpr("null"));
					counter++;
				}
				//System.err.println("methodCallExpr " + methodCallExpr);
			}
		}

		return clusteredMethod;
	}
	
	/** This will check if there is a method with the same name
	 * It doens't check if the method is the same or anything else but just if there is a same method name 
	 * @param methodToAdd
	 * @param addToClass
	 * @return
	 */
	private boolean methodSameName(MethodDeclaration methodToAdd, ClassOrInterfaceDeclaration addToClass) {
		List<MethodDeclaration> methods = addToClass.findAll(MethodDeclaration.class);
		for(MethodDeclaration method : methods) 
			if(method.getNameAsString().equals(methodToAdd.getNameAsString())) 
				return true;		
		return false;
	}
	
	/*Assert Call Analyzer */
	
	/** This method will analyze a Single Assert instruction
	 * 
	 * @param methodTestSuite
	 * @param pageObject
	 * @param pageObjectVariable
	 * @param expression
	 */
	private void analyzeAssertCallExpStmt(
			MethodDeclaration methodTestSuite, ClassOrInterfaceDeclaration pageObject, String pageObjectVariable,
			ExpressionStmt expression, String methodName, String lastLocator) {	
		BlockStmt bodyMethod;
		MethodCallExpr assertCall = (MethodCallExpr) expression.getExpression();		
		//The first node contains the assert, the second contains the methodCall to the locator
//		MethodCallExpr firstArgumentInvocation = (MethodCallExpr) assertCall.getChildNodes().get(1);
//		MethodCallExpr findElementInvocation = (MethodCallExpr) firstArgumentInvocation.getChildNodes().get(0);
		List<Node> childNodes = assertCall.getChildNodes(); 		
//		MethodDeclaration methodPO = searchGetterInPO(pageObject,methodName);
		
//		if(methodPO==null) {
			//create the method PO statement			
		MethodDeclaration methodPO = new MethodDeclaration() 					
					.setName(methodName)
					.setPublic(true);
		bodyMethod = methodPO.getBody().get();
		//Node 0 is the commnad
		//Node 1 is the Locator
		//Node 2 is the optional value to check			
		switch(childNodes.get(0).toString()) {
			case "assertEquals":
			case "assertThat":							
				methodPO.setType("String");
				break;
			case "assertTrue":
			case "assertFalse":							
				methodPO.setType("boolean");
				break;
			default:						
				throw new UnsupportedOperationException("No conversion found for this istruction: " +childNodes.get(0).toString() );					
		}												
		MethodCallExpr methodCall = (MethodCallExpr) childNodes.get(1);
		lastLocator = createWaitForElement((ExpressionStmt) expression,bodyMethod,false);
		bodyMethod.addStatement("return " + methodCall+";");	
		
		//Add Method To PO
		//If the body of the new method is already present 
		//I have to use that method
		methodPO = addMethod(methodPO,pageObject,null,null,null,null,false,null);	
		
//		}	
		//Now add the assert in the Main Function	
		bodyMethod = methodTestSuite.getBody().get();	
		//Create the statement
		String statement = createAssertWithNormalStmt(childNodes,pageObjectVariable, methodPO, null);									
		bodyMethod.addStatement(statement);		
	}

	/** Search if the getter is already defined in the PageObject
	 * if it is already defined the method is return else null
	 * 
	 * @param pageObject
	 * @param findElementCall
	 * @return
	 */
	private MethodDeclaration searchGetterInPO(ClassOrInterfaceDeclaration pageObject, String methodName) {
		for(MethodDeclaration method : pageObject.findAll(MethodDeclaration.class)) {
			if(method.getNameAsString().equals(methodName)) 
				return method;
		}
		return null;
	}
	
	/** Generate the name for a getter Call
	 * 
	 * @param findElementInvocation
	 * @return
	 */
	private String generateNameForGetterCalls(MethodCallExpr findElementInvocation) {
		//The third element contains the Locator invocation
		MethodCallExpr locatorInvocation = findElementInvocation;
		List<Node> nodes = locatorInvocation.getChildNodes();
		//[By, id/css/others, 'identifier']		
		String baseGetter = "get";
		if(findElementInvocation.toString().contains("findElements"))
			baseGetter = "getList";
		return baseGetter+nodes.get(1).toString().toUpperCase()+"_"+cleanCharacterForMethod(nodes.get(2).toString());
	}
	
	/** Replace invalid character like - , ", : and . () for a methodDeclaration
	 * 
	 * @param value
	 * @return
	 */
	private String cleanCharacterForMethod(String value) {
		List<String> valueToReplace = new LinkedList<String>();
		valueToReplace.add("\"");
		valueToReplace.add(":");
		valueToReplace.add(".");
		valueToReplace.add("(");
		valueToReplace.add(")");
		valueToReplace.add("'");
		valueToReplace.add("\\");
		valueToReplace.add("/");
		valueToReplace.add("#");
		valueToReplace.add("[");
		valueToReplace.add("]");
		valueToReplace.add("@");
		valueToReplace.add("=");
		valueToReplace.add("*");
		valueToReplace.add(" ");
		valueToReplace.add(">");
		value =  value.replace("-","_");
		for(String removeThis : valueToReplace) {
			value = value.replace(removeThis, "");
		}
		return value;
			
	}

	/** This method will analyze a BlockStmt assert instruction 
	 * From the first instruction the name of the Getter is recovered
	 * From the second last the return attribute is recorded
	 * 
	 * @param methodTestSuite
	 * @param lastPageVariable
	 * @param methodPO
	 * @param bodyMethod
	 * @param blockInstruction
	 * @param values
	 * @param argumentsName
	 * @param methodName
	 * @param lastLocator
	 */
	private void generateAssertCallBlockStmt(MethodDeclaration methodTestSuite,ClassOrInterfaceDeclaration pageObject,
			String lastPageVariable,
			 BlockStmt blockInstruction,List<Node> values,List<NameExpr> argumentsName,
			 String methodName, String lastLocator) {		
		List<Node> childs = blockInstruction.getChildNodes();
		BlockStmt bodyMethod;
		//The first instruction contains the variable declaration
		ExpressionStmt stmt = (ExpressionStmt) childs.get(0);		
//		VariableDeclarationExpr variableExpr = (VariableDeclarationExpr) stmt.getChildNodes().get(0);
//		VariableDeclarator variable = (VariableDeclarator)variableExpr.getChildNodes().get(0);
		//the variable child contains: Type of return value, variable name, operation
//		MethodCallExpr findElement = (MethodCallExpr) variable.getChildNodes().get(2);
		//If the method call is something like: driver.findElement(By..).getValue(..) then the first MethodCall is the correct argument
//		if(findElement.getChildNodes().size()>=3 && findElement.getChildNodes().get(0) instanceof MethodCallExpr) 	
//				findElement = (MethodCallExpr) findElement.getChildNodes().get(0);
//		MethodDeclaration methodPO = searchGetterInPO(pageObject,methodName);
		
//		if(methodPO==null) {
		MethodDeclaration	methodPO = new MethodDeclaration() 					
					.setName(methodName)
					.setPublic(true);
		bodyMethod = methodPO.getBody().get();	
		Node lastNode = null;
		for(Node child : childs) {
			if(child.toString().startsWith("assert")) {					
				VariableDeclarationExpr variableDeclExp = (VariableDeclarationExpr) lastNode.getChildNodes().get(0);
				methodPO.setType(variableDeclExp.getElementType());
				VariableDeclarator variableDecl = (VariableDeclarator)variableDeclExp.getChildNodes().get(0);
				if (checkLocator(child,lastLocator))
					lastLocator = createWaitForElement((ExpressionStmt) child,bodyMethod,false);
				bodyMethod.addStatement("return " +variableDecl.getNameAsString()+";");		
				//Add Method To PO
				//If the body of the new method is already present 
				//I have to use that method
				methodPO = addMethod(methodPO,pageObject,values,argumentsName,null,null,false,null);						
			}else {
				ExpressionStmt expStmt = (ExpressionStmt)child.clone();
				analyzeMethodArguments(expStmt,values,argumentsName);
				if (checkLocator(child,lastLocator))
					lastLocator = createWaitForElement((ExpressionStmt) child,bodyMethod,false);
				bodyMethod.addStatement(expStmt);
			}
			lastNode = child;
		}
//		}
		String testSuiteMethodcallStmt;
		bodyMethod = methodPO.getBody().get();
		Node lastInstruction = childs.get(childs.size()-1);
		if( lastInstruction instanceof AssertStmt) { //this is a special case
			Node secondLastNode = childs.get(childs.size()-2);
			VariableDeclarationExpr variableDeclExp = (VariableDeclarationExpr) secondLastNode.getChildNodes().get(0);
			VariableDeclarator variableDecl = (VariableDeclarator)variableDeclExp.getChildNodes().get(0);
			
			testSuiteMethodcallStmt = createAssertWithAssertStmt(lastPageVariable, methodPO, values, lastInstruction, variableDecl);	
		}else {
			List<Node> childNodes = ((MethodCallExpr)((ExpressionStmt)lastInstruction).getExpression()).getChildNodes(); 
			testSuiteMethodcallStmt = createAssertWithNormalStmt(childNodes,lastPageVariable, methodPO, values);		
		}	
		bodyMethod = methodTestSuite.getBody().get();
		bodyMethod.addStatement(testSuiteMethodcallStmt);
		values.clear();
		argumentsName.clear();
	}
	
	/** Create the String instruction for an assert, with the optional argument call list
	 * 
	 * @param childNodes
	 * @param lastPageVariable
	 * @param methodPO
	 * @param values
	 * @return
	 */
	private String createAssertWithNormalStmt(List<Node> childNodes,String lastPageVariable, MethodDeclaration methodPO, List<Node> values) {
		String statement = childNodes.get(0).toString() +"(";		
		statement+=lastPageVariable+"."+methodPO.getNameAsString()+"(";
		statement+=argumentParser(values, null);	
		statement+=")";
		for(int i=2;i<childNodes.size();i++)
			statement+=","+childNodes.get(i);					
		statement+=");";
		return statement;
	}
	
	/** Create an assert base statement, that means the entire check is execute in an expression like that assert(somethingTrue/False)
	 * 
	 * @param lastPageVariable
	 * @param methodPO
	 * @param values
	 * @param assertNode
	 * @param variableDecl
	 * @return
	 */
	private String createAssertWithAssertStmt(String lastPageVariable, MethodDeclaration methodPO, List<Node> values,
			Node assertNode, VariableDeclarator variableDecl) {
		String statement;
		AssertStmt asserStmt = (AssertStmt) assertNode;
		EnclosedExpr enclosedExp = (EnclosedExpr) asserStmt.getChildNodes().get(0);
		BinaryExpr binaryExpr = (BinaryExpr) enclosedExp.getChildNodes().get(0);
		statement = "assert("; 					
		statement+=lastPageVariable+"."+methodPO.getNameAsString()+"(";
		statement+=argumentParser(values,null);					
		statement+=")";
		statement+= binaryExpr.getLeft().toString().replaceFirst(variableDecl.getNameAsString(), "");
		statement+=" "+binaryExpr.getOperator().asString()+" "+binaryExpr.getRight()+");";
		return statement;
	}
	
	/* Delimiter Searcher*/
	/** Check if the instruction {SeleniumIDEExt}backToMain is found
	 * @param exp
	 * @return 
	 */
	private boolean _checkDelimiterEnd(Node exp) {
		return DELIMITER_BACK_TO_MAIN.equals(exp.toString()); 
	}
	
	private boolean _checkDelimiterChildEnd(Node exp) {
		return DELIMITER_BACK_TO_FATHER.equals(exp.toString()); 
	}
	
	/** Check if the instruction is a System.out.println(\"{SeleniumIDEExt} Delimiter
	 *  
	 * @param exp
	 * @return null if it's not a delimiter, otherwise will return the map of the elements pageObject/pageMethod
	 */
	private Map<String, String> checkDelimiterInstruction(Node exp) {
		if(!exp.toString().contains(DELIMITER_PO_DECLARATION)) 
			return null;
		Map<String,String> hashMap = new HashMap<>();
		Optional<MethodCallExpr> callExp = exp.findFirst(MethodCallExpr.class);
		if(callExp.isEmpty()) return null;
		
		LiteralExpr argument = callExp.get().getArgument(0).asLiteralExpr();
		String[] values = argument.toString().replaceAll("\"","").split(":");
		if(values.length!=3) 
			return null;
		
		hashMap.put(KEY_HASH_PO_NAME, getPOName(values[1]));
		hashMap.put(KEY_HASH_PO_METHOD, values[2]);		
		return hashMap;
	}
	
	private String getPOName(String input) {
		if(this.normalize) {
			input = input.toLowerCase();
		}
		return PO_PREFIX+input;
	}
	
	
	/** Argument Analyzer */
	
	/** This method check of the value is contained in the  inner list
	 * 
	 * @param inner
	 * @param val
	 * @return true or false
	 */
	private boolean contains(List<?> inner, String val) {
		for (Object object : inner) {
			if(val.equals(object.toString())) 
				return true;
		}
		return false;
	}
	
	/** This method will create a String with all the argument to use to call a Mehtod
	 * 
	 * @param values
	 * @param innert
	 * @return "" or a string of arguments call
	 */
	private String argumentParser(List<?> values, List<?> inner) {
		List<String> statemens = new LinkedList<String>();
		if(values!=null && values.size()>0) {
			for(int i=0;i<values.size();i++) 
				if(inner == null || !contains(inner,values.get(i).toString()))
					statemens.add(values.get(i).toString());					
		}		
		return String.join(",", statemens);
	}


	/** For each ExpressionStmt will search if it's a MethodCallExpr that contains sendKeys or By.xpath
	 * In that case it will change the node inside the call and replace it with a variable
	 * Then will add this variable and the original value in memory for create the correct call and method invocation
	 * @param expStmt 
	 * @param values list of real values
	 * @param variables list of variable
	 */
	private void analyzeMethodArguments(ExpressionStmt expStmt,List<Node> values,List<NameExpr> variables) {
		
		if(!(expStmt.getChildNodes().get(0) instanceof MethodCallExpr)) 
			return;	
		
		boolean containsSendKeys = expStmt.toString().contains("sendKeys");
		boolean containsXPath  = expStmt.toString().contains("By.xpath");
		
		if(!containsSendKeys && !containsXPath) 		
			return; //no sendKeys or Xpath means no argument to check		
		
		MethodCallExpr methodCall = (MethodCallExpr) expStmt.getChildNodes().get(0);		
		List<Node> childs = methodCall.getChildNodes();	
		if(containsSendKeys) 
			extractArgumentFromSendKeys(childs,methodCall, values, variables  );
		if(containsXPath) 	{	
			if(childs.get(0) instanceof NameExpr) { //add data in variables				
				extractArgumentFromXPath((MethodCallExpr)childs.get(3).getChildNodes().get(0),values, variables );
			}else {			
				extractArgumentFromXPath((MethodCallExpr)childs.get(0),values, variables );
			}
		}
		
		
	}
	
	/** Extract the argument from the sendKeys command
	 * 
	 * @param values
	 * @param variables
	 * @param methodCall
	 * @param childs
	 */
	private void extractArgumentFromSendKeys(List<Node> childs, MethodCallExpr methodCall,    List<Node> values, List<NameExpr> variables
			) {
		for(int i=0;i<childs.size();i++) {
			Node node = childs.get(i);
			if(node instanceof SimpleName) {
				SimpleName name = (SimpleName)node;
				if("sendKeys".equals(name.toString())) {
					//the next one is the argument
					i++;
					node = childs.get(i);	
					if(!(node instanceof StringLiteralExpr)) continue; //only text write on keyboard, no press key like Enter or else	
					values.add(childs.get(i));				
					//now i need to replace this value with a variable					
					NameExpr var = new NameExpr("key"+values.size());					
					variables.add(var);
					methodCall.replace(node,var);	
				}
			}
		}
	}
	
	/** Extract the argument from the By.xpath call
	 * 
	 * @param methodCallExpr
	 * @param values
	 * @param variables
	 */
	private void extractArgumentFromXPath(MethodCallExpr methodCallExpr, List<Node> values, List<NameExpr> variables)
	{	
		List<Node> childs = methodCallExpr.getChildNodes();
		for(int i=0;i<childs.size();i++) {
			Node node = childs.get(i);		
			if(node instanceof MethodCallExpr) {
				MethodCallExpr xPathCall = (MethodCallExpr)node;				
				if(xPathCall.toString().startsWith("By.xpath")) {
					//the child is: By, xpath, realPathString
					
					Node literal = xPathCall.getChildNodes().get(2);
					if(!(literal instanceof StringLiteralExpr)) continue; 	
					
					if(!literal.toString().contains("value=") && !literal.toString().contains("text=") && !literal.toString().contains(". ="))
						continue;//No variabile to look at
	
					String[] optionSplit = literal.toString().replaceAll("(\\\\)", "").split("'");
					String methodArgument = optionSplit[0];
					for(int k=1;k<optionSplit.length;k++) {
						if(k%2==1) { // -> "//input[@name=\'status2\' and @value=\'Listed\']" -> odd = variable, pair = command
							String variableCommand;
							if(optionSplit[k-1].toString().contains(". =") || optionSplit[k-1].toString().contains("text=") || optionSplit[k-1].toString().contains("value=") ) {
								values.add(new StringLiteralExpr(optionSplit[k]));	
								NameExpr var = new NameExpr("key"+values.size());
								variables.add(var);		
								variableCommand ="\"+"+ var.getNameAsString() +"+\"";
							}else {
								variableCommand = optionSplit[k];
							}							
							methodArgument+="\'"+ variableCommand +"\'";							
						}else {
							methodArgument+=optionSplit[k];
						}
					}							
					MethodCallExpr newMethodCall = new MethodCallExpr("By.xpath");
					newMethodCall.addArgument(methodArgument);			
					methodCallExpr.replace(node,newMethodCall);					
				}				
			}			
		}
	}
	

	/* Logs */	
	/** Return all the logs that happen during the compilation
	 * 
	 * @return list of logs
	 */
	public List<String> getLogs() {
		return logs;
	}
	
	/** Create a Log of warning for something happen
	 * 
	 * @param log
	 */
	private void addWarning(String log) {
		addLog("Warning: " +log);
	}
	
	/** Add the log to the List of logs with the time stamp of when it happen
	 * 
	 * @param log
	 */	
	@SuppressWarnings("deprecation")
	private void addLog(String log) {
		logs.add((new Date()).toLocaleString() +" - "+log);
	}
	
	/** Return the main CompilationUnit where all the TestMethod is declared
	 * 
	 * @return
	 */

	public CompilationUnit getTestSuiteUnit() {		
		return centralUnit;
	}	
}
