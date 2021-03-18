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
 * Tokiwa GmbH, Berlin
 *
 * Source of class Tag
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;


/**
 * Tag is an expression that converts a value to a choice type, i.e., it adds a
 * tag to the value.
 *
 * NYI: Tag should not be part of AST, but part of the IR.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class Tag extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * the original value instance.
   */
  public Expr _value;


  /**
   * The desired tagged type, set during creation.
   */
  public Type _taggedType;


  /**
   * Clazz index for value clazz that is being boxed and, at
   * _valAndRefClazzId+1, reference clazz that is the result clazz of the
   * boxing.
   */
  public int _valAndTaggedClazzId = -1;  // NYI: Used by dev.flang.be.interpreter, REMOVE!


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param value the value instance
   */
  public Tag(Expr value, Type taggedType)
  {
    super(value.pos);

    if (PRECONDITIONS) require
      (pos != null,
       value != null);

    this._value = value;
    this._taggedType = taggedType;
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
    return _taggedType;
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
  public Tag visit(FeatureVisitor v, Feature outer)
  {
    _value = _value.visit(v, outer);
    v.action(this, outer);
    return this;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return "tag(" + _value + ")";
  }

}

/* end of file */