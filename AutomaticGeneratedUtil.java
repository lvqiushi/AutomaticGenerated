package com.example.web;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.mhc.bs.common.base.APIResult;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.RootDoc;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**  
 * <p> 生成接口文档工具类（根据注释） 需要依赖jar包：java8、fastJson</p>
 *   
 * @author: xiucai（xiucai@maihaoche.com） -
 * @date: 2018/8/9 16:30   
 * @since V1.0
 */
public class AutomaticGeneratedUtil {
	// javadoc解析出的所有类信息都在该类中
	private static RootDoc root;

	// 基本类型的包装类列表（无需递归遍历属性）
	private static List<Class<?>> primitiveWrapperTypeList = new LinkedList<>();

	// 基本类型的列表
	private static List<String> primitiveNameList = new LinkedList<>();

	// 去除空白字符的正则
	private static Pattern crearSpace = Pattern.compile("\\s*|\t|\r|\n");

	// 用于存放List中泛型实际类型的map,因为一些原因，无法获取到list实际类型，用map预解析存放
	// key:参数名（在一次生成接口文档的运行中，参数名不要相同，会出问题）
	// value:list中实际对象类型
	private static Map<String,String> GENERIC_CLASS_MAP = new HashMap<>();

	static {
		//特殊类列表的初始化
		primitiveWrapperTypeList.add(Boolean.class);
		primitiveWrapperTypeList.add(Byte.class);
		primitiveWrapperTypeList.add(Character.class);
		primitiveWrapperTypeList.add(Double.class);
		primitiveWrapperTypeList.add(Float.class);
		primitiveWrapperTypeList.add(Integer.class);
		primitiveWrapperTypeList.add(Long.class);
		primitiveWrapperTypeList.add(Short.class);
		primitiveWrapperTypeList.add(Date.class);
		primitiveWrapperTypeList.add(BigDecimal.class);
		primitiveWrapperTypeList.add(LocalDateTime.class);
		primitiveWrapperTypeList.add(LocalDate.class);
		primitiveWrapperTypeList.add(LocalTime.class);
		//基本类型列表初始化
		primitiveNameList.add("boolean");
		primitiveNameList.add("byte");
		primitiveNameList.add("char");
		primitiveNameList.add("short");
		primitiveNameList.add("int");
		primitiveNameList.add("long");
		primitiveNameList.add("folat");
		primitiveNameList.add("double");
	}

	// 自定义的Doclet对象
	public static class Doclet {
		public Doclet() {
		}
		public static boolean start(RootDoc root) {
			AutomaticGeneratedUtil.root = root;
			return true;
		}
	}

	public static RootDoc getRoot() {
		return root;
	}

	public AutomaticGeneratedUtil() { }

	// 接口生成工具主要方法(调用入口:需要手动指定需要生成接口文档java文件的路径)
	public static void main(final String ... args) throws Exception {
		// 这个需要自己指定要生成接口文档的方法中包含的所有类在那几个包下(本地绝对文件路径)，不同包之间使用分号隔开
		// example: /idea/test/web/src/main/java;/idea/test/api/src/main/java
		String sourcePackages = "/idea/test/web/src/main/java;/idea/test/api/src/main/java";
		// 解析对象列表，手动指定的话，请自行在列表中添加对象路径，
		// 例如 anlysisaJavaPath.add("yourJavaPath")
		List<String> anlysisaJavaPath = new LinkedList<>();
		// 要生成文档的类名称（全限定名）
		// 例如 com.mhc.web.dubbo.TestDubbo
		String className = "com.example.web.dubbo.TestDubbo";

		// 我会根据输入的包名，递归遍历包下包含的所有java对象，并加入到解析列表中用于解析
		// 如果你不害怕麻烦，本工具同样支持输入精确java对象的路径列表,并加入到解析列表中
		if (StringUtils.isEmpty(sourcePackages) && CollectionUtils.isEmpty(anlysisaJavaPath)) {
			System.out.println("你什么路径也不给，还想生成接口文档，我很难帮你办事啊");
			return;
		}
		// 获取java文件路径列表
		getJavaFileListByPath(sourcePackages,anlysisaJavaPath);
		// 初始化系统参数
		String[] initParams = getRunParams(anlysisaJavaPath);

		// 调用com.sun.tools.javadoc.Main执行javadoc
		// -doclet 指定自己的docLet类名
		// 请在要生成接口文档的项目中运行该方法，否则classpath错误会无法反射获取对象属性
		com.sun.tools.javadoc.Main.execute(initParams);
		// 具体生成接口文档的方法
		show(className);
	}
	/**    
	 * 遍历所有包路径中的java文件
	 *
	 * @param packagesList 需要遍历的包路径列表
	 * @param anlysisaJavaPath 解析对象列表
	 * @return void
	 */
	public static void getJavaFileListByPath(String packagesList,List<String> anlysisaJavaPath) {
		if (StringUtils.isEmpty(packagesList)) {
			return;
		}
		String[] packagePathArray = packagesList.split(";");
		for (String packagePath:packagePathArray) {
			recursiveGetJavaPath(packagePath,anlysisaJavaPath);
		}
	}

	/**    
	 * 根据包路径递归遍历所有的java对象
	 *
	 * @param packagePath 需要遍历的包路径
	 * @param anlysisaJavaPath 解析对象列表
	 * @return void
	 */
	public static void recursiveGetJavaPath(String packagePath,List<String> anlysisaJavaPath) {
		File dir;
		try {
			dir = new File(packagePath);
		} catch (Exception e) {
			System.out.println("这个文件路径 "+ packagePath +" 有问题啊，兄弟，再看看吧");
			System.exit(0);
			return;
		}
		File[] files = dir.listFiles();

		if (files != null) {
			for (int i = 0; i < files.length; i++) {
				String fileName = files[i].getName();
				if (files[i].isDirectory()) {
					recursiveGetJavaPath(files[i].getAbsolutePath(),anlysisaJavaPath);
				} else if (fileName.endsWith(".java")) { // 判断文件名是否为java文件
					// 获取文件绝对路径并加入到解析对象列表中
					String javaPath = files[i].getAbsolutePath();
					anlysisaJavaPath.add(javaPath);
				} else {
					continue;
				}
			}
		}
	}

	/**    
	  * 合并系统参数和java文件路径的方法
	 *
	 * @param anlysisaJavaPath  java文件路径
	 * @return java.lang.String[]
	 */
	public static String[] getRunParams(List<String> anlysisaJavaPath) {
		String[] javaPathArray = anlysisaJavaPath.toArray(new String[anlysisaJavaPath.size()]);
		if (javaPathArray.length < 1) {
			System.out.println("一个java对象都没有,你搞错路径了吧");
			System.exit(0);
		}
		// 系统初始化参数
		String[] runParam = new String[] {
				"-doclet", Doclet.class.getName(),
				"-encoding", "utf-8",
				"-private",//获取私有属性和方法
		};
		int paramLength = runParam.length;
		runParam = Arrays.copyOf(runParam, paramLength + javaPathArray.length);
		System.arraycopy(javaPathArray, 0, runParam, paramLength, javaPathArray.length);
		return runParam;
	}

	// 根据注释生成接口文档的方法
	public static void show(String className) {
		//String className = "com.example.web.dubbo.TestDubbo";
		ClassDoc interfaceClass = root.classNamed(className);
		//ClassDoc[] classes = root.classes();  // 调试代码，可以查看有多少类被加载进来

		if (null == interfaceClass) {
			System.out.println("未搜索到指定文件,请检查包路径选项是否正确");
			return;
		}
		try {
			Class<?> clazz = Class.forName(className);
			// 首先遍历类的所有属性，对集合类的参数名和实际泛型类建立映射关系(预解析过程)
			for (Method method:clazz.getDeclaredMethods()) {
				for (java.lang.reflect.Parameter parameter:method.getParameters()) {
					//if(parameter.getType().getName().startsWith("java.util.List")) {
					if (isCollection(Class.forName(parameter.getType().getName()))) {
						java.lang.reflect.ParameterizedType paramType = (java.lang.reflect.ParameterizedType) parameter.getParameterizedType();
						GENERIC_CLASS_MAP.put(parameter.getName(),paramType.getActualTypeArguments()[0].getTypeName());
						Class<?> paramClazz = Class.forName(paramType.getActualTypeArguments()[0].getTypeName());
						creatGenericeClassMap(paramClazz);
					} else if (parameter.getType().getName().startsWith("[")) {
						// 数组可以直接获取实际类型，暂不需要预解析
//						Class<?> paramClazz = Class.forName(parameter.getType().getName());
//						GENERIC_CLASS_MAP.put(parameter.getName(),paramClazz.getComponentType().getTypeName());
//						creatGenericeClassMap(paramClazz.getComponentType());
						continue;
					} else {
						Class<?> paramClazz = Class.forName(parameter.getType().getName());
						creatGenericeClassMap(paramClazz);
					}
				}
				// 遍历方法出参  如果出参是普通对象则传递对象进行遍历，如果出参是List，则取出实际对象进行遍历
				java.lang.reflect.ParameterizedType returnType = (java.lang.reflect.ParameterizedType) method.getGenericReturnType();
				if (null == returnType.getActualTypeArguments() || returnType.getActualTypeArguments().length < 1) {
					creatGenericeClassMap(method.getReturnType());
				} else {
					java.lang.reflect.ParameterizedType nsetingReturnType = null;
					// 如果可以强转成功，则说明该类型有嵌套类型存在。
					try {
						nsetingReturnType = (java.lang.reflect.ParameterizedType) returnType.getActualTypeArguments()[0];
						creatGenericeClassMap(Class.forName(nsetingReturnType.getActualTypeArguments()[0].getTypeName()));
					} catch (ClassCastException e) {
						creatGenericeClassMap(Class.forName(returnType.getActualTypeArguments()[0].getTypeName()));
					}
				}
			}

			// 遍历对象方法（不支持父类的方法，对外的api接口怎么可能还有继承）
			for (Method method:clazz.getDeclaredMethods()) {
				System.out.printf("%s\n", "打印方法注释");
				// 再遍历doc中带注释的方法一次，匹配方法名，打印方法注释
				for (MethodDoc methodDoc:interfaceClass.methods()) {
					if (methodDoc.name().equals(method.getName())) {
						if (methodDoc.commentText() != null) {
							String methodComment = formatComment(methodDoc.commentText());
							System.out.println(methodComment);
						}
					}
				}
				System.out.printf("%s\n", "打印入参文档");
				Map paramMap = new HashMap();
				// 遍历方法中的入参，并递归遍历，用map存放每个参数的类结构，再使用序列化成json格式输出
				for (java.lang.reflect.Parameter parameter:method.getParameters()) {
					// 如果属性是基本类型，则直接输出
					if (isWrap(parameter.getType().getTypeName()) || isWrapClass(parameter.getType())) {
						paramMap.put(parameter.getName(),parameter.getType().getSimpleName());
						continue;
					}
					// 如果属性为集合类，获取实际类型进行递归
					//if(parameter.getType().getName().startsWith("java.util.List")) {
					if (isCollection(Class.forName(parameter.getType().getName()))) {
						java.lang.reflect.ParameterizedType paramType = (java.lang.reflect.ParameterizedType) parameter.getParameterizedType();
						AutomaticGeneratedUtil
								.putMap(parameter.getName(),parameter.getType().getName(),paramMap,paramType.getActualTypeArguments()[0].getTypeName(),null);
					} else {
						AutomaticGeneratedUtil
								.putMap(parameter.getName(),parameter.getType().getName(),paramMap,null,null);
					}
				}
				String formJsonString = JSONObject.toJSONString(paramMap);
				System.out.println(formatPrint(formJsonString));

				System.out.printf("%s\n", "打印出参文档");
				boolean containsList = false;
				boolean useResult = false;
				Map returnMap = new HashMap();
				if (method.getReturnType().getTypeName().startsWith("java.util.List")) {
					java.lang.reflect.ParameterizedType returnType = (java.lang.reflect.ParameterizedType) method.getGenericReturnType();
					AutomaticGeneratedUtil.putMap("return",method.getReturnType().getTypeName(),returnMap,returnType.getActualTypeArguments()[0].getTypeName(),null);
				} else {
					java.lang.reflect.ParameterizedType returnType = (java.lang.reflect.ParameterizedType) method.getGenericReturnType();
					if (null == returnType.getActualTypeArguments() || returnType.getActualTypeArguments().length < 1) {
						AutomaticGeneratedUtil
								.putMap("return", method.getReturnType().getTypeName(), returnMap, null,null);
					} else {
						if (method.getReturnType().getTypeName().contains("Result")) {
							useResult = true;
							try {
								java.lang.reflect.ParameterizedType nsetingReturnType = (java.lang.reflect.ParameterizedType) returnType.getActualTypeArguments()[0];
								AutomaticGeneratedUtil
										.putMap("return", nsetingReturnType.getActualTypeArguments()[0].getTypeName(), returnMap, null,null);
								containsList = true;
							} catch (ClassCastException e) {
								AutomaticGeneratedUtil
										.putMap("return", returnType.getActualTypeArguments()[0].getTypeName(), returnMap, null,null);
							}
						} else {
							// TODO 如果入参为APIResult<List<xxx>> 形式，目前有bug存在，data中的属性会不是list类型，后续会重构处理掉
							try {
								java.lang.reflect.ParameterizedType nsetingReturnType = (java.lang.reflect.ParameterizedType) returnType.getActualTypeArguments()[0];
								AutomaticGeneratedUtil.putMap("return", method.getReturnType().getTypeName(), returnMap, nsetingReturnType.getActualTypeArguments()[0].getTypeName(), null);
							} catch (ClassCastException e) {
								AutomaticGeneratedUtil.putMap("return", method.getReturnType().getTypeName(), returnMap, null, null);
							}
						}
					}
				}
				try {
					Map valueMap = (HashMap) returnMap.get("return");
					String returnJsonString = JSONObject.toJSONString(valueMap, SerializerFeature.WriteMapNullValue);
					JSONObject resultJson = null;
					if (useResult) {
						APIResult result = new APIResult();
						resultJson = JSONObject.parseObject(JSONObject.toJSONString(result, SerializerFeature.WriteMapNullValue));
					} else {
						System.out.println(formatPrint(returnJsonString));
					}
					if (containsList) {
						resultJson.put("data","[ " + returnJsonString + " ]");
						System.out.println(formatPrint(JSONObject.toJSONString(resultJson, SerializerFeature.WriteMapNullValue)));
					} else {
						resultJson.put("data",returnJsonString);
						System.out.println(formatPrint(JSONObject.toJSONString(resultJson, SerializerFeature.WriteMapNullValue)));
					}
				} catch (ClassCastException e){
					String value = (String) returnMap.get("return");
					System.out.println(formatPrint(value));
				}
			}
		} catch (ClassNotFoundException e){
			System.out.println("未能反射获取到类的属性，请检查classpath是否正确");
			return;
		}
	}

	/**     
	 * 递归遍历对象，genericClassNameInList只有在上级对象为List的时候才传入，表示为对象实际类型
	 * 
	 * @param formName 属性名
	 * @param formType 属性类型
	 * @param map 每次递归时，收集本次递归所遍历出的所有属性
	 * @param genericClassNameInList list对象实际类型
	 * @param genericClassType xxxResult对象实际类型  专门为我司的各种result准备的字段
	 * @return void 
	 */
	public static void putMap(String formName, String formType, Map map,String genericClassNameInList,String genericClassType) {
		ClassDoc formClass = root.classNamed(formType);

		Class<?> clazz;
		try {
			// 对Result中的泛型字段做特殊处理
			if (null != genericClassType && "java.lang.Object".equals(formType)) {
				clazz = Class.forName(genericClassType);
				formClass = root.classNamed(genericClassType);
			} else {
				clazz = Class.forName(formType);
			}
			// 如果属性为数组类型
			if (clazz.isArray() || formType.startsWith("[")) {
				Class arrayFiledClass = clazz.getComponentType();
				// 如果集合类实际泛型类是基本类型，则直接输出
				if (isWrap(formType) || isWrapClass(arrayFiledClass)) {
					map.put(formName,"[ "+arrayFiledClass.getSimpleName()+" ]");
				} else {
					Map arrayMap = new HashMap();
					putMap(arrayFiledClass.getTypeName(), arrayFiledClass.getTypeName(), arrayMap, null,null);
					String collectionJson = "[ " + JSONObject.toJSONString(arrayMap.get(arrayFiledClass.getTypeName())) + " ]";
					map.put(formName, collectionJson);
				}
				return;
			}
			// 如果属性为集合类型
			if (isCollection(clazz)) {
				Class<?> collectionClazz = Class.forName(genericClassNameInList);
				// 如果集合类实际泛型类是基本类型，则直接输出
				if (isWrapClass(collectionClazz)) {
					map.put(formName,"[ "+collectionClazz.getTypeName()+" ]");
				} else {
					// 如果集合类实际泛型类不是基本类型，继续递归遍历所有属性
					Map collectionMap = new HashMap();
					putMap(collectionClazz.getTypeName(), collectionClazz.getTypeName(), collectionMap, null,null);
					String collectionJson = "[ " + JSONObject.toJSONString(collectionMap.get(collectionClazz.getTypeName())) + " ]";
					map.put(formName, collectionJson);
				}
				return;
			}
			// 接口文档生成暂时不支持map类型的数据
			if (isMap(clazz)) {
				map.put(formName,"map类型");
				return;
			}

		} catch (ClassNotFoundException e) {
			System.out.println("递归对象"+formType+"时，发生错误,反射无法生成该对象");
			return;
		}

		Map formMap = new HashMap();
		for (FieldDoc fieldDoc:formClass.fields()) {
			try {
				fieldDoc.type().getElementType();
				if ("java.util.Map".equals(fieldDoc.type().qualifiedTypeName())) {
					continue;
				}
				// 对Result中的泛型字段做特殊处理
				if (null != genericClassType && fieldDoc.qualifiedName().contains("Result.data")) {
					Map<String,String> recursiveMap = new HashMap();
					AutomaticGeneratedUtil
							.putMap(fieldDoc.name(), fieldDoc.type().qualifiedTypeName(), recursiveMap, null,genericClassType);
					formMap.put(fieldDoc.name(), recursiveMap.get(fieldDoc.name()));
					continue;
				}
				if ("java.lang.Object".equals(fieldDoc.type().qualifiedTypeName())) {
					continue;
				}
				if (AutomaticGeneratedUtil.onlyHasWrapClass(clazz.getTypeName())) {
					if (!StringUtils.isEmpty(fieldDoc.commentText())) {
						String fieldComment = formatComment(fieldDoc.commentText());
						formMap.put(fieldDoc.name(), fieldDoc.type().simpleTypeName() + "//" + fieldComment);
					} else {
						formMap.put(fieldDoc.name(), fieldDoc.type().simpleTypeName());
					}
					continue;
				}

				if (isWrap(fieldDoc.type().qualifiedTypeName()) || isWrapClass(Class.forName(fieldDoc.type().qualifiedTypeName()))) {
					if (!StringUtils.isEmpty(fieldDoc.commentText())) {
						String fieldComment = formatComment(fieldDoc.commentText());
						formMap.put(fieldDoc.name(), fieldDoc.type().simpleTypeName() + "//" + fieldComment);
					} else {
						formMap.put(fieldDoc.name(), fieldDoc.type().simpleTypeName());
					}
					continue;
				}

				//if(fieldDoc.type().qualifiedTypeName().startsWith("java.util.List")) {
				if (isCollection(Class.forName(fieldDoc.type().qualifiedTypeName()))) {
					// 反射获取list的实际类型
					String fieldDocGenericClassName = GENERIC_CLASS_MAP.get(fieldDoc.name());
					Class<?> collectionClazz = Class.forName(fieldDocGenericClassName);
					// 如果实际类型为基本类型，则直接输出,否则继续递归
					if (isWrapClass(collectionClazz)) {
						if (!StringUtils.isEmpty(fieldDoc.commentText())) {
							String fieldComment = formatComment(fieldDoc.commentText());
							formMap.put(fieldDoc.name(), "[ " + collectionClazz.getSimpleName() + " ]" + "//" + fieldComment);
						} else {
							formMap.put(fieldDoc.name(), "[ " + collectionClazz.getSimpleName() + " ]");
						}
					} else {
						Map<String,String> recursiveMap = new HashMap();
						AutomaticGeneratedUtil.putMap(fieldDoc.name(), fieldDoc.type().qualifiedTypeName(), recursiveMap,
								fieldDocGenericClassName,null);
						formMap.put(fieldDoc.name(), recursiveMap.get(fieldDoc.name()));
					}
					continue;
				}else {
					Map<String,String> recursiveMap = new HashMap();
					AutomaticGeneratedUtil
							.putMap(fieldDoc.name(), fieldDoc.type().qualifiedTypeName(), recursiveMap, null,null);
					formMap.put(formName, recursiveMap);
					continue;
				}
			} catch (ClassNotFoundException e) {
				System.out.println("对象"+fieldDoc.type().qualifiedTypeName()+"，发生错误,反射无法生成该对象");
				continue;
			}

		}
		map.put(formName,formMap);
		return;
	}

	/**    
	 * 判断类中是否只包含基本类型
	 *
	 * @param formType  对象全限定类名
	 * @return boolean
	 */
	public static boolean onlyHasWrapClass(String formType) throws ClassNotFoundException {
		Class<?> clazz =Class.forName(formType);

		boolean onlyHasWrap = false;

		for (Field field:clazz.getDeclaredFields()) {
			Class<?> fileClass = field.getType();
			if (isWrapClass(fileClass) || isWrap(formType)) {
				onlyHasWrap = true;
			}
			// 如果有一个属性不是基本属性就直接返回false  每遍历一个属性就重置onlyHasWrap
			if (onlyHasWrap) {
				onlyHasWrap = false;
			} else {
				return false;
			}
		}
		return true;
	}

	/**    
	 * 判断class是否为集合类型
	 *
	 * @param clazz
	 * @return boolean
	 */
	public static boolean isCollection(Class<?> clazz){
		return Collection.class.isAssignableFrom(clazz);
	}

	/**    
	 * 判断class是否为map类型
	 *
	 * @param clazz
	 * @return boolean
	 */
	public static boolean isMap(Class<?> clazz){
		return Map.class.isAssignableFrom(clazz);
	}

	/**    
	 * 根据名称判断是否为基本类型
	 *
	 * @param typeName  基本类型的名称 比如 int,double..
	 * @return boolean
	 */
	public static boolean isWrap(String typeName) {
		for (String wrapperNmae : primitiveNameList) {
			if (wrapperNmae.equals(typeName)) {
				return true;
			}
		}
		return false;
	}

	/**    
	 * 判断对象是否为基本类型的包装类或者一些特殊类
	 *
	 * @param clazz
	 * @return boolean
	 */
	public static boolean isWrapClass(Class<?> clazz) {
//		if(clazz.isPrimitive() ) {
//			return true;
//		}
		for (Class<?> resolvedWrapper : primitiveWrapperTypeList) {
			if(clazz.equals(resolvedWrapper) ) {
				return true;
			}
		}
		if (clazz.equals(String.class)) {
			return true;
		}
		if (clazz.getTypeName().startsWith("java.time.")
				|| clazz.getTypeName().startsWith("java.util.Optional")
				|| clazz.getTypeName().startsWith("java.util.concurrent")) {
			return true;
		}
		return false;
	}

	// 预解析对象时的方法
	public static void creatGenericeClassMap(Class<?> clazz) {
		try {
			if (isWrapClass(clazz) || onlyHasWrapClass(clazz.getName())) {
				return;
			}
			for (Field field:clazz.getDeclaredFields()) {
				if (isCollection(Class.forName(field.getType().getTypeName()))) {
					java.lang.reflect.ParameterizedType paramType = (java.lang.reflect.ParameterizedType) field.getGenericType();
					GENERIC_CLASS_MAP.put(field.getName(),paramType.getActualTypeArguments()[0].getTypeName());
				} else {
					Class<?> paramClazz = Class.forName(field.getType().getName());
					creatGenericeClassMap(paramClazz);
				}
			}
		} catch (ClassNotFoundException e){
			System.out.println("无法通过反射获取类的信息，请检查classpath是否正确");
			return;
		}
	}

	/**    
	 * 格式化注释内容 去除一些前后空行、回车、制表符、开头*号等内容
	 *
	 * @param comment 注释信息
	 * @return java.lang.String
	 */
	public static String formatComment(String comment) {
		Matcher m = crearSpace.matcher(comment);
		String afterFormat = m.replaceAll("").replaceAll("\\u00A0","");
		if (afterFormat.startsWith("*")) {
			afterFormat.replaceFirst("\\*","");
		}
		return afterFormat;
	}

	/**    
	 * 格式化接口文档输出内容 主要为去掉[ ]前后的双引号
	 *
	 * @param comment 注释信息
	 * @return java.lang.String
	 */
	public static String formatPrint(String comment) {
		return comment.replaceAll("\\\\","").replaceAll("\"\\[","[").replaceAll("\\]\"","]");
	}

}
