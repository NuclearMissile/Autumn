package org.example.scan

import org.example.autumn.annotation.Component
import org.example.autumn.annotation.Primary

interface IInterfaceBean

@Component
class InterfaceBean : IInterfaceBean

interface IInterfaceBean2

@Primary
@Component
class InterfaceBean2 : IInterfaceBean2

@Component
class InterfaceBean22 : IInterfaceBean2