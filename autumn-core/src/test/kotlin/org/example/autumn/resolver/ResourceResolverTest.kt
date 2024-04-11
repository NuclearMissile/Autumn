package org.example.autumn.resolver

import kotlin.test.Test
import kotlin.test.assertTrue


class ResourceResolverTest {
    @Test
    fun scanClass() {
        val pkg = "org.example.scan"
        val classes = ResourceResolver(pkg).scanResources { res ->
            val name = res.name
            if (name.endsWith(".class"))
                name.substring(0, name.length - 6).replace("/", ".").replace("\\", ".")
            else null
        }.sorted()
        val listClasses = arrayOf(
            // list of some scan classes:
            "org.example.scan.ValueConverterBean",
            "org.example.scan.AnnotationDestroyBean",
            "org.example.scan.SpecifyInitConfiguration",
            "org.example.scan.OriginBean",
            "org.example.scan.FirstProxyBeanPostProcessor",
            "org.example.scan.SecondProxyBeanPostProcessor",
            "org.example.scan.OuterBean",
            "org.example.scan.OuterBean\$NestedBean",
            "org.example.scan.sub1.Sub1Bean",
            "org.example.scan.sub1.sub2.Sub2Bean",
            "org.example.scan.sub1.sub2.sub3.Sub3Bean",
        )
        for (clazz in listClasses) {
            assertTrue(classes.contains(clazz))
        }
    }
}