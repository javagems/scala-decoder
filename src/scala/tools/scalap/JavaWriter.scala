/*     ___ ____ ___   __   ___   ___
**    / _// __// _ | / /  / _ | / _ \    Scala classfile decoder
**  __\ \/ /__/ __ |/ /__/ __ |/ ___/    (c) 2003-2006, LAMP/EPFL
** /____/\___/_/ |_/____/_/ |_/_/
**
*/

// $Id: JavaWriter.scala 5838 2006-02-23 17:54:21Z michelou $

package scala.tools.scalap

import java.io._


class JavaWriter(classfile: Classfile, writer: Writer) extends CodeWriter(writer) {

  val cf = classfile

  def flagsToStr(clazz: Boolean, flags: Int) = {
    val buffer = new StringBuffer()
    var x: StringBuffer = buffer
    if (((flags & 0x0007) == 0) &&
      ((flags & 0x0002) != 0))
      x = buffer.append("private ")
    if ((flags & 0x0004) != 0)
      x = buffer.append("protected ")
    if ((flags & 0x0010) != 0)
      x = buffer.append("final ")
    if ((flags & 0x0400) != 0)
      x = if (clazz) buffer.append("abstract ")
          else buffer.append("/*deferred*/ ")
    buffer.toString()
  }

  def nameToClass(str: String) = {
    val res = Names.decode(str.replace('/', '.'))
    if (res == "java.lang.Object") "scala.Any" else res
  }

  def nameToClass0(str: String) = {
    val res = Names.decode(str.replace('/', '.'))
    if (res == "java.lang.Object") "scala.AnyRef" else res
  }

  def nameToSimpleClass(str: String) =
    Names.decode(str.substring(str.lastIndexOf('/') + 1))

  def nameToPackage(str: String) = {
    val inx = str.lastIndexOf('/')
    val name = if (inx == -1) str else str.substring(0, inx).replace('/', '.')
    Names.decode(name)
  }

  def sigToType(str: String): String =
    sigToType(str, 0)._1

  def sigToType(str: String, i: Int): Pair[String, Int] = str.charAt(i) match {
    case 'B' => Pair("scala.Byte", i + 1)
    case 'C' => Pair("scala.Char", i + 1)
    case 'D' => Pair("scala.Double", i + 1)
    case 'F' => Pair("scala.Float", i + 1)
    case 'I' => Pair("scala.Int", i + 1)
    case 'J' => Pair("scala.Long", i + 1)
    case 'S' => Pair("scala.Short", i + 1)
    case 'V' => Pair("scala.Unit", i + 1)
    case 'Z' => Pair("scala.Boolean", i + 1)
    case 'L' => val j = str.indexOf(';', i);
          Pair(nameToClass(str.substring(i + 1, j)), j + 1)
    case '[' => val Pair(tpe, j) = sigToType(str, i + 1);
          Pair("scala.Array[" + tpe + "]", j)
    case '(' => val Pair(tpe, j) = sigToType0(str, i + 1);
          Pair("(" + tpe, j)
    case ')' => val Pair(tpe, j) = sigToType(str, i + 1);
          Pair("): " + tpe, j)
  }

  def sigToType0(str: String, i: Int): Pair[String, Int] =
    if (str.charAt(i) == ')')
      sigToType(str, i)
    else {
      val Pair(tpe, j) = sigToType(str, i)
      if (str.charAt(j) == ')') {
        val Pair(rest, k) = sigToType(str, j)
        Pair(tpe + rest, k)
      } else {
        val Pair(rest, k) = sigToType0(str, j)
        Pair(tpe + ", " + rest, k)
      }
    }

  def getName(n: Int): String = cf.pool(n) match {
    case cf.UTF8(str) => str
    case cf.StringConst(m) => getName(m)
    case cf.ClassRef(m) => getName(m)
    case x => "<error>"
  }

  def getClassName(n: Int): String = nameToClass(getName(n))

  def getSimpleClassName(n: Int): String = nameToSimpleClass(getName(n))

  def getPackage(n: Int): String = nameToPackage(getName(n))

  def getType(n: Int): String = sigToType(getName(n))

  def isStatic(flags: Int) = (flags & 0x0008) != 0

  def isInterface(flags: Int) = (flags & 0x0200) != 0

  def isConstr(name: String) = (name == "<init>")

  def printField(flags: Int, name: Int, tpe: Int, attribs: List[cf.Attribute]) = {
    print(flagsToStr(false, flags))
    if ((flags & 0x0010) != 0)
      print("val " + Names.decode(getName(name)))
    else
      print("final var " + Names.decode(getName(name)))
    print(": " + getType(tpe) + ";").newline
  }

  def printMethod(flags: Int, name: Int, tpe: Int, attribs: List[cf.Attribute]) = {
    if (getName(name) == "<init>")
    print(flagsToStr(false, flags))
    attribs find {
      case cf.Attribute(name, _) => getName(name) == "JacoMeta"
    } match {
      case Some(cf.Attribute(_, data)) =>
        val mp = new MetaParser(getName(
          ((data(0) & 0xff) << 8) + (data(1) & 0xff)).trim())
        mp.parse match {
          case None =>
            if (getName(name) == "<init>") {
              print("def this" + getType(tpe) + ";").newline
            } else {
              print("def " + Names.decode(getName(name)))
              print(getType(tpe) + ";").newline
            }
          case Some(str) =>
            if (getName(name) == "<init>")
              print("def this" + str + ";").newline
            else
              print("def " + Names.decode(getName(name)) + str + ";").newline
        }
      case None =>
        if (getName(name) == "<init>") {
          print("def this" + getType(tpe) + ";").newline
        } else {
          print("def " + Names.decode(getName(name)))
          print(getType(tpe) + ";").newline
      }
    }
    attribs find {
      case cf.Attribute(name, _) => getName(name) == "Exceptions"
    } match {
      case Some(cf.Attribute(_, data)) =>
        val n = ((data(0) & 0xff) << 8) + (data(1) & 0xff)
        indent.print("throws ")
        for (val i <- Iterator.range(0, n) map {x => 2 * (x + 1)}) {
          val inx = ((data(i) & 0xff) << 8) + (data(i+1) & 0xff)
          if (i > 2) print(", ")
          print(getClassName(inx).trim())
        }
        undent.newline
      case None =>
    }
  }

  def printClassHeader = {
    if (isInterface(cf.flags)) {
      print("trait " + getSimpleClassName(cf.classname))
    } else {
      print("class " + getSimpleClassName(cf.classname))
      if (cf.pool(cf.superclass) != null)
        print(" extends " + nameToClass0(getName(cf.superclass)))
    }
    cf.interfaces foreach {
      n => print(" with " + getClassName(n))
    }
  }

  def printClass = {
    val pck = getPackage(cf.classname);
    if (pck.length() > 0)
      println("package " + pck + ";")
    print(flagsToStr(true, cf.flags))
    cf.attribs find {
      case cf.Attribute(name, _) => getName(name) == "JacoMeta"
    } match {
      case None =>
        printClassHeader;
      case Some(cf.Attribute(_, data)) =>
        val mp = new MetaParser(getName(
          ((data(0) & 0xff) << 8) + (data(1) & 0xff)).trim());
        mp.parse match {
          case None => printClassHeader;
          case Some(str) =>
            if (isInterface(cf.flags))
              print("trait " + getSimpleClassName(cf.classname) + str);
            else
              print("class " + getSimpleClassName(cf.classname) + str);
        }
    }
    var statics: List[cf.Member] = Nil
    print(" {").indent.newline
    cf.fields foreach {
      case m@cf.Member(_, flags, name, tpe, attribs) =>
        if (isStatic(flags))
          statics = m :: statics
        else
          printField(flags, name, tpe, attribs)
    }
    cf.methods foreach {
      case m@cf.Member(_, flags, name, tpe, attribs) =>
        if (isStatic(flags))
          statics = m :: statics
        else
          printMethod(flags, name, tpe, attribs)
    }
    undent.print("}").newline
    if (!statics.isEmpty) {
      print("object " + getSimpleClassName(cf.classname) + " {")
      indent.newline
      statics foreach {
        case cf.Member(true, flags, name, tpe, attribs) =>
          printField(flags, name, tpe, attribs)
        case cf.Member(false, flags, name, tpe, attribs) =>
          if (getName(name) != "<clinit>")
            printMethod(flags, name, tpe, attribs)
      }
      undent.print("}").newline
    }
  }

}
