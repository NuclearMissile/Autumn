package com.example.autumn.resolver

import kotlin.test.Test
import kotlin.test.assertTrue


class ResourceResolverTest {
    @Test
    fun scanClass() {
        val pkg = "com.example.scan"
        val classes = ResourceResolver(pkg).scanResources { res ->
            val name = res.name
            if (name.endsWith(".class"))
                name.substring(0, name.length - 6).replace("/", ".").replace("\\", ".")
            else null
        }.sorted()
        val listClasses = arrayOf(
            // list of some scan classes:
            "com.example.scan.ValueConverterBean",
            "com.example.scan.AnnotationDestroyBean",
            "com.example.scan.SpecifyInitConfiguration",
            "com.example.scan.OriginBean",
            "com.example.scan.FirstProxyBeanPostProcessor",
            "com.example.scan.SecondProxyBeanPostProcessor",
            "com.example.scan.OuterBean",
            "com.example.scan.OuterBean\$NestedBean",
            "com.example.scan.sub1.Sub1Bean",
            "com.example.scan.sub1.sub2.Sub2Bean",
            "com.example.scan.sub1.sub2.sub3.Sub3Bean",
        )
        for (clazz in listClasses) {
            assertTrue(classes.contains(clazz))
        }
    }
}