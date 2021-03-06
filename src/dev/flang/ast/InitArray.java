/*

This file is part of the Fuzion language implementation.

The Fuzion language implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language implementation is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
License for more details.

You should have received a copy of the GNU General Public License along with The
Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.

*/

/*-----------------------------------------------------------------------
 *
 * Tokiwa Software GmbH, Germany
 *
 * Source of class InitArray
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * InitArray represents syntactic sugar for array initialization: '[1, 2, 3]' or
 * '[point x, y; point sin alpha, cos alpha]'.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class InitArray extends Expr
{


  /*-------------------------  static variables -------------------------*/


  /**
   * quick-and-dirty way to make unique names for temporary variables needed for
   * array initializtion.
   */
  static private long _id_ = 0;


  /*----------------------------  variables  ----------------------------*/


  /**
   * The elements to be stored in the array
   */
  public final List<Expr> _elements;


  /**
   * The type of this array.
   */
  private Type type_;


  /**
   * Clazz index for array clazz
   */
  public int _arrayClazzId = -1;  // NYI: Used by dev.flang.be.interpreter, REMOVE!


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param elements the elements of this array
   */
  public InitArray(SourcePosition pos, List<Expr> elements)
  {
    super(pos);
    this._elements = elements;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * typeOrNull returns the type of this expression or Null if the type is still
   * unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or null if not known.
   */
  public Type typeOrNull()
  {
    if (type_ == null)
      {
        var t = Types.resolved.t_void;
        for (var e : _elements)
          {
            t = t.union(e.type());
          }
        type_ = Types.intern(new Type(pos(),
                                      "array",
                                      new List<>(t),
                                      null,
                                      Types.resolved.f_array,
                                      Type.RefOrVal.LikeUnderlyingFeature));
      }
    return type_;
  }


  /**
   * During type inference: Inform this expression that it is used in an
   * environment that expects the given type.  In particular, if this
   * expression's result is assigned to a field, this will be called with the
   * type of the field.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this expression
   *
   * @param t the expected type.
   *
   * @return either this or a new Expr that replaces thiz and produces the
   * result. In particular, if the result is assigned to a temporary field, this
   * will be replaced by the statement that reads the field.
   */
  public Expr propagateExpectedType(Resolution res, Feature outer, Type t)
  {
    if (type_ == null)
      {
        var elementType = elementType(t);
        if (elementType != Types.t_ERROR)
          {
            for (var e : _elements)
              {
                e.propagateExpectedType(res, outer, elementType);
              }
            type_ = t;
          }
      }
    return this;
  }


  /**
   * For a given array type, return the type t of its elements.
   *
   * @param t any type
   *
   * @param if t is Array<T>; the element type T. Types.t_ERROR otherwise.
   */
  private Type elementType(Type t)
  {
    if (PRECONDITIONS) require
      (t != null);

    if (t.featureOfType() == Types.resolved.f_array &&
        t._generics.size() == 1)
      {
        return t._generics.get(0);
      }
    else
      {
        return Types.t_ERROR;
      }
  }


  /**
   * For this array's type(), return the element type
   *
   * @param if type() is Array<T>; the element type T. Types.t_ERROR otherwise.
   */
  private Type elementType()
  {
    return elementType(type());
  }


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this.
   */
  public Expr visit(FeatureVisitor v, Feature outer)
  {
    var li = _elements.listIterator();
    while (li.hasNext())
      {
        var e = li.next();
        li.set(e.visit(v, outer));
      }
    return v.action(this, outer);
  }


  /**
   * Boxing for actual arguments: Find actual arguments of value type that are
   * assigned to formal argument types that are references and box them.
   *
   * @param outer the feature that contains this expression
   */
  public void box(Feature outer)
  {
    var elementType = elementType();
    var li = _elements.listIterator();
    while (li.hasNext())
      {
        var e = li.next();
        li.set(e.box(elementType));
      }
  }


  /**
   * check the types in this assignment
   *
   * @param outer the root feature that contains this statement.
   */
  public void checkTypes()
  {
    if (PRECONDITIONS) require
      (type_ != null);

    var elementType = elementType();

    for (var e : _elements)
      {
        Type actlT = e.type();

        check
          (actlT == Types.intern(actlT));

        check
          (Errors.count() > 0 || (elementType != Types.t_ERROR &&
                                  actlT != Types.t_ERROR    ));

        if (!elementType.isAssignableFromOrContainsError(actlT))
          {
            FeErrors.incompatibleTypeInArrayInitialization(e.pos(), type_, elementType, actlT, e);
          }
      }
  }


  /**
   * Resolve syntactic sugar, e.g., by replacing anonymous inner functions by
   * declaration of corresponding inner features. Add (f,<>) to the list of
   * features to be searched for runtime types to be layouted.
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this statement.
   */
  public Expr resolveSyntacticSugar2(Resolution res, Feature outer)
  {
    Expr result = this;
    if (true)  // NYI: This syntactic sugar should not be resolved if this array is a compile-time constant
      {
        var eT           = new List<Type>(elementType());
        var lengthArgs   = new List<Expr>(new IntConst(_elements.size()));
        var sys          = new Call(pos(), null, "sys"                  ).resolveTypes(res, outer);
        var sysArrayCall = new Call(pos(), sys , "array", eT, lengthArgs).resolveTypes(res, outer);
        var sysT         = new Type(pos(), "sys", Type.NONE, null);
        var sysArrayT    = new Type(pos(), "array", eT, sysT);
        var sysArrayName = "#initArraySys" + (_id_++);
        var sysArrayVar  = new Feature(pos, Consts.VISIBILITY_LOCAL, sysArrayT, sysArrayName, null, outer);
        sysArrayVar.findDeclarations(outer);
        res.resolveDeclarations(sysArrayVar);
        res.resolveTypes();
        var sysArrayAssign = new Assign(res, pos(), sysArrayVar, sysArrayCall, outer);
        var stmnts = new List<Stmnt>(sysArrayAssign);
        for (var i = 0; i < _elements.size(); i++)
          {
            var e = _elements.get(i);
            var setArgs         = new List<Expr>(new IntConst(i), e);
            var readSysArrayVar = new Call(e.pos(), null           , sysArrayName          ).resolveTypes(res, outer);
            var setElement      = new Call(e.pos(), readSysArrayVar, "index [ ] =", setArgs).resolveTypes(res, outer);
            stmnts.add(setElement);
          }
        var readSysArrayVar = new Call(pos(), null, sysArrayName                ).resolveTypes(res, outer);
        var sysArrArgs      = new List<Expr>(readSysArrayVar);
        var arrayCall       = new Call(pos(), null, "array"     , eT, sysArrArgs).resolveTypes(res, outer);
        stmnts.add(arrayCall);
        result = new Block(pos(), stmnts);
      }
    return result;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return _elements.toString("[", "; ", "]");
  }

}

/* end of file */
