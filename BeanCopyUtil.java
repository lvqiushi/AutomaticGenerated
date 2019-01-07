package com.mhc.landrover.core.common.utils;

import ma.glasnost.orika.BoundMapperFacade;
import ma.glasnost.orika.CustomConverter;
import ma.glasnost.orika.MapperFacade;
import ma.glasnost.orika.MapperFactory;
import ma.glasnost.orika.converter.ConverterFactory;
import ma.glasnost.orika.impl.DefaultMapperFactory;
import ma.glasnost.orika.metadata.TypeFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**  
 * <p> 对Orika 对象转换进行再封装的工具类 </p>
 * <p> 有使用心得可以相互借鉴 </p>
 * <p> 欢迎提供新的需求 </p>
 *   
 * @author: xiucai（xiucai@maihaoche.com） 
 * @date: 2018/7/3 17:25   
 * @since V1.0
 */
public class BeanCopyUtil {

	private static MapperFacade mapper;

	private static MapperFactory MAPPER_FACTORY = null;

	/**
	 * 类型转换缓存
	 */
	private static Map<Class<?>,BoundMapperFacade> mapperFacadeMap = new ConcurrentHashMap<>();

	/**  
	 * factory 和 默认mapper的初始化  
	 */
	static {
		MAPPER_FACTORY = new DefaultMapperFactory.Builder().build();
		mapper = MAPPER_FACTORY.getMapperFacade();
	}

	// 使用默认的对象转换设置，支持不同类型Bean的相同属性名的嵌套拷贝(只测试过三层嵌套的使用情况)
	// 性能比手动set的慢几十倍左右。
	public static <S,T> T copy(S sourceBean,Class<T> targetClass){
		return mapper.map(sourceBean,targetClass);
	}

	// 将boundMapper进行缓存，对象转换性能提高将近半倍
	// !!!!如果一个原类型对应多个目标类需要转换，请不要使用缓存进行转换!!!!!
	public static <S,T> T copyWithCache(S sourceBean,Class<S> sourceClass,Class<T> targetClass){
		return getBoundMapper(sourceClass,targetClass).map(sourceBean);
	}

	/**
	 *  List -> List的属性转换方法，
	 *  example:
	 *       List<T> tList  ->  List<P> pList
	 *       pList = BeanCopyUtil.copyList(tList,P.getClass);
	 */
	public static <S,T> List<T> copyList(Iterable<S> sourceList,Class<T> targetClass){
		return mapper.mapAsList(sourceList,targetClass);
	}

	/**
	 * List -> List的属性转换方法，(性能比上个方法可以提高3-5倍)
	 *
	 * 不建议使用mapper.mapAsList(Iterable<S>,Class<T>)接口, sourceClass需要反射，实在有点慢
	 */
	public static <S, D> List<D> copyListFaster(Iterable<S> sourceList, Class<S> sourceClass, Class<D> destinationClass) {
		return mapper.mapAsList(sourceList, TypeFactory.valueOf(sourceClass), TypeFactory.valueOf(destinationClass));
	}

	// 将使用过的BoundMapperFacade缓存起来，提高性能
	private static<S,T> BoundMapperFacade<S,T> getBoundMapper(Class<S> sourceClass,Class<T> targetClass){
		BoundMapperFacade boundMapperFacade = mapperFacadeMap.get(sourceClass);
		if (null != boundMapperFacade) {
			return boundMapperFacade;
		} else {
			registerClassMap(sourceClass,targetClass);
			BoundMapperFacade<S, T> boundMapper =
					MAPPER_FACTORY.getMapperFacade(sourceClass, targetClass);
			mapperFacadeMap.put(sourceClass,boundMapper);
			return boundMapper;
		}
	}

	//将转换类型注册进工厂中
	private static<S,T> void registerClassMap(Class<S> sourceClass,Class<T> targetClass) {
		MAPPER_FACTORY.classMap(sourceClass,targetClass).byDefault().register();
		//mapper = MAPPER_FACTORY.getMapperFacade();
	}

	//配置全局自定义转换器
	private static void registerConverter(CustomConverter customConverter) {
		ConverterFactory converterFactory = MAPPER_FACTORY.getConverterFactory();
		converterFactory.registerConverter(customConverter);
	}

//	/**
//	 * 预先获取orika转换所需要的Type，避免每次转换,提高性能
//	 */
//	public static <E> Type<E> getType(final Class<E> rawType) {
//		return TypeFactory.valueOf(rawType);
//	}
}
