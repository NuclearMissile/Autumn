package io.nuclearmissile.scan

import io.nuclearmissile.autumn.annotation.Component
import io.nuclearmissile.autumn.annotation.Primary

interface IInterfaceBean

@Component
class InterfaceBean : IInterfaceBean

interface IInterfaceBean2

@Primary
@Component
class InterfaceBean2 : IInterfaceBean2

@Component
class InterfaceBean22 : IInterfaceBean2