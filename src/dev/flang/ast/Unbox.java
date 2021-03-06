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
 * Source of class Unbox
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Iterator;

import dev.flang.util.Errors;
import dev.flang.util.SourcePosition;

/**
 * Unbox is an expression that dereferences an address of a value type to
 * the value type.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Unbox extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The address of the value type
   */
  public Expr adr_;


  /**
   * The type of this, set during creation.
   */
  private Type type_;


  /**
   * This this Unbox needed, i.e, not a NOP. This might be a NOP if this is
   * used as a reference.
   */
  public boolean _needed = false;


  /**
   * Clazz index for value clazz that is being unboxed and, at
   * _refAndValClazzId+1, value clazz that is the result clazz of the unboxing.
   */
  public int _refAndValClazzId = -1;  // NYI: Used by dev.flang.be.interpreter, REMOVE!


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param t the result type
   */
  public Unbox(SourcePosition pos, Expr adr, Type type, Feature outer)
  {
    super(pos);

    if (PRECONDITIONS) require
      (pos != null,
       adr != null,
       adr.type().isRef(),
       Errors.count() > 0 || type.featureOfType() == outer,
       !type.featureOfType().isThisRef()
       );

    this.adr_ = adr;
    this.type_ = Types.intern(type); // outer.thisType().resolve(outer);
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
    return type_;
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
  public Unbox visit(FeatureVisitor v, Feature outer)
  {
    adr_ = adr_.visit(v, outer);
    v.action(this, outer);
    return this;
  }


  /**
   * Check if this value might need boxing, unboxing or tagging and wrap this
   * into Box()/Tag() if this is the case.
   *
   * @param frmlT the formal type this is assigned to.
   *
   * @return this or an instance of Box wrapping this.
   */
  Expr box(Type frmlT)
  {
    var t = type();
    if (t != Types.resolved.t_void &&
        ((!frmlT.isRef() ||
          (frmlT.isChoice() &&
           !frmlT.isAssignableFrom(t) &&
           frmlT.isAssignableFrom(t.asValue())))))
      {
        this._needed = true;
        this.type_ = frmlT;
      }
    return super.box(frmlT);
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return "deref(" + adr_ + ")";
  }

}

/* end of file */
