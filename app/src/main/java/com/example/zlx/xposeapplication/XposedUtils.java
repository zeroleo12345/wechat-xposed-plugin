package com.example.zlx.xposeapplication;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

public class XposedUtils {
    private XposedUtils() {}

    private static final HashMap<String, Field> fieldCache = new HashMap<>();
    private static final HashMap<String, Method> methodCache = new HashMap<>();


    // 查找方法, 带有Cache
    public static Method findMethodByReturnType(Class<?> targetClass, String methodName, Class<?> returnType, Class<?>[] paramsType) {
        String fullMethodName = targetClass.getName() + ";->" + methodName + getParametersString(paramsType) + returnType.getName();

        if (methodCache.containsKey(fullMethodName)) {
            Method method = methodCache.get(fullMethodName);
            if (method == null)
                throw new NoSuchMethodError(fullMethodName);
            return method;
        }

        try {
            Method method = findMethodRecursiveImpl(targetClass, methodName, returnType, paramsType);
            methodCache.put(fullMethodName, method);
            return method;
        } catch (NoSuchMethodError e) {
            methodCache.put(fullMethodName, null);
            throw new NoSuchMethodError(fullMethodName);
        }
    }

    // 内部递归查找方法
    public static Method findMethodRecursiveImpl(Class<?> targetClass, String methodName, Class<?> returnType, Class<?>[] paramsType) throws NoSuchMethodError {
//        String fullMethodName = targetClass.getName() + ";->" + methodName + getParametersString(paramsType) + returnType.getName();
        Class clazz = targetClass;
        do {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getReturnType() == returnType && method.getName().equals(methodName)) {
                    Class[] parameterTypes = method.getParameterTypes();
                    if (paramsType.length != parameterTypes.length) {
//                        XLog.w(targetClass.getName() + '#' + methodName + getParametersString(parameterTypes) + "#mismatch");
//                        XLog.w("input param length:%d, function param length:%d", paramsType.length, parameterTypes.length);
                        continue;
                    }
                    boolean isMatch = true;
                    for (int i = 0; i < paramsType.length; i++) {
                        if (paramsType[i] != parameterTypes[i]) {
//                            XLog.w(targetClass.getName() + '#' + methodName + getParametersString(parameterTypes) + "#mismatch");
//                            XLog.w("input param length:%d, function param length:%d", paramsType.length, parameterTypes.length);
                            isMatch = false;
                            break;
                        }
                    }
                    if (isMatch) {
                        method.setAccessible(true);
                        return method;
                    }
                }
            }
            clazz = clazz.getSuperclass();
        } while (clazz != null);
//        XLog.e("not found method:%s", fullMethodName);
        throw new NoSuchMethodError();
    }

    // note 调用类实例方法
    public static Object mycallMethod(Object targetObject, String methodName, Class<?> returnType) throws Exception {
        Method method = findMethodByReturnType( targetObject.getClass(), methodName, returnType, new Class[]{});
        return method.invoke(targetObject);
    }
    public static Object mycallMethod(Object targetObject, String methodName, Class<?> returnType, Class<?>[] paramsType, Object... params) throws Exception {
        Method method = findMethodByReturnType( targetObject.getClass(), methodName, returnType, paramsType );
        return method.invoke(targetObject, params);
    }

    // note 调用静态方法
    public static Object mycallStaticMethod(Class<?> staticClassType, String methodName, Class<?> returnType, Class<?>[] paramsType, Object... params) throws Exception {
        Method method = findMethodByReturnType( staticClassType, methodName, returnType, paramsType );
        return method.invoke(null, params);
    }

    public static Field findFirstFieldByExactType(Class<?> targetClass, Class<?> fieldType) {
        String fullFieldName = targetClass.getName() + ";->" + "#FirstField" + ":" + fieldType.getName();

        if (fieldCache.containsKey(fullFieldName)) {
            Field field = fieldCache.get(fullFieldName);
            if (field == null)
                throw new NoSuchFieldError(fullFieldName);
            return field;
        }

        try {
            Field field = findFieldRecursiveImpl(targetClass, fieldType);
            fieldCache.put(fullFieldName, field);
            return field;
        } catch (NoSuchFieldException e) {
            fieldCache.put(fullFieldName, null);
            throw new NoSuchFieldError(fullFieldName);
        }
    }

    // 内部递归查找字段
    public static Field findFieldRecursiveImpl(Class<?> targetClass, Class<?> fieldType) throws NoSuchFieldException {
        Class<?> clz = targetClass;
        do {
            for (Field field : clz.getDeclaredFields()) {
                if (field.getType() == fieldType) {
                    field.setAccessible(true);
                    return field;
                }
            }
        } while ((clz = clz.getSuperclass()) != null);
        throw new NoSuchFieldError("Field of type " + fieldType.getName() + " in class " + targetClass.getName());
    }

    // 查找字段, 带有Cache
    public static Field findFieldByType(Class<?> targetClass, String fieldName, Class<?> fieldType) {
        String fullFieldName = targetClass.getName() + ";->" + fieldName + ":" + fieldType.getName();

        if (fieldCache.containsKey(fullFieldName)) {
            Field field = fieldCache.get(fullFieldName);
            if (field == null)
                throw new NoSuchFieldError(fullFieldName);
            return field;
        }

        try {
            Field field = findFieldRecursiveImpl(targetClass, fieldName, fieldType);
            fieldCache.put(fullFieldName, field);
            return field;
        } catch (NoSuchFieldException e) {
            fieldCache.put(fullFieldName, null);
            throw new NoSuchFieldError(fullFieldName);
        }
    }

    // 内部递归查找字段
    public static Field findFieldRecursiveImpl(Class<?> targetClass, String fieldName, Class<?> fieldType) throws NoSuchFieldException {
        Class clazz = targetClass;
        do {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType() == fieldType && field.getName().equals(fieldName)) {
                    field.setAccessible(true);
                    return field;
                }
            }
            clazz = clazz.getSuperclass();
        } while (clazz != null);
        throw new NoSuchFieldError("Field of type " + fieldType.getName() + " in class " + targetClass.getName());
    }

    // note 获取类实例第一个符合类型的对象
    public static Object mygetFirstObjectField(Object targetObject, Class<?> fieldType) throws Exception{
        Class targetClass = targetObject.getClass();
        Field field = XposedUtils.findFirstFieldByExactType(targetClass, fieldType);
        return field.get(targetObject);
    }

    // note 获取类实例对象
    public static Object mygetObjectField(Object targetObject, String fieldName, Class<?> fieldType) throws Exception{
        Class clazz = targetObject.getClass();
        Field field = findFieldByType(clazz, fieldName, fieldType);
        return field.get(targetObject);
    }

    // note 设置类实例对象
    public static void mysetObjectField(Object targetObject, String fieldName, Class<?> fieldType, Object value) throws Exception{
        Class clazz = targetObject.getClass();
        Field field = findFieldByType(clazz, fieldName, fieldType);
        field.set(targetObject, value);
    }

    // 可变Class<?>参数转成String, 用于获取参数列表类名, 自带括号
    private static String getParametersString(Class<?>... clazzes) {
        StringBuilder sb = new StringBuilder("(");
        boolean first = true;
        for (Class<?> clazz : clazzes) {
            if (first)
                first = false;
            else
                sb.append(",");

            if (clazz != null)
                sb.append(clazz.getCanonicalName());
            else
                sb.append("null");
        }
        sb.append(")");
        return sb.toString();
    }

}