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
 * Source of class Types
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.SourcePosition;

/*---------------------------------------------------------------------*/


/**
 * Types manages the types used in the system.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Types extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  private static final Map<Type, Type> types = new TreeMap<>();

  /**
   * Name of abstract features for function types:
   */
  public static final String FUNCTION_NAME = "Function";

  public static Resolved resolved = null;

  /**
   * Dummy name used for address type Types.t_ADDRESS which is used for
   * references to value types.
   */
  static final String ADDRESS_NAME = "--ADDRESS--";


  /**
   * Dummy name used for undefined type t_UNDEFINED which is used for undefined
   * types that are expected to be replaced by the correct type during type
   * inference.  Examples are the result of union of distinct types on different
   * branches of an if or match, or the type of the result var befure type
   * inference has determined the result type.
   */
  static final String UNDEFINED_NAME = "--UNDEFINED--";


  /**
   * Dummy name used for error type t_ERROR which is used in case of compilation
   * time errors.
   */
  static final String ERROR_NAME = Errors.ERROR_STRING;


  /**
   * Names if internal types that are not backed by physical feature defintions.
   */
  static Set<String> INTERNAL_NAMES = Collections.<String>unmodifiableSet
    (new TreeSet<>(Arrays.asList(ADDRESS_NAME,
                                 UNDEFINED_NAME,
                                 ERROR_NAME)));

  /* artificial type for the address of a value type, used for outer refs to value instances */
  public static Type t_ADDRESS = new Type(ADDRESS_NAME);

  /* artificial type for Expr that does not have a well defined type such as the
   * union of two distinct types */
  public static Type t_UNDEFINED = new Type(UNDEFINED_NAME);

  /* artificial type for Expr with unknown type due to compilation error */
  public static Type t_ERROR = new Type(ERROR_NAME);

  /* artificial feature used when feature is not known due to compilation error */
  public static Feature f_ERROR = new Feature(true);

  public static class Resolved
  {
    public final Feature universe;
    public final Type t_i32 ;
    public final Type t_u32 ;
    public final Type t_i64 ;
    public final Type t_u64 ;
    public final Type t_ref_i32 ;
    public final Type t_ref_u32 ;
    public final Type t_ref_i64 ;
    public final Type t_ref_u64 ;
    public final Type t_bool;
    public final Type t_object;
    public final Type t_sys;
    public final Type t_string;
    public final Type t_conststring;
    public final Type t_unit;

    /* void will be used as the initial result type of tail recursive calls of
     * the form
     *
     *    f => if c f else x
     *
     * since the union of void  with any other type is the other type.
     */
    public final Type t_void;
    public final Feature f_void;
    public final Feature f_choice;
    public final Feature f_TRUE;
    public final Feature f_FALSE;
    public final Feature f_bool;
    public final Feature f_bool_NOT;
    public final Feature f_bool_AND;
    public final Feature f_bool_OR;
    public final Feature f_bool_IMPLIES;
    public final Feature f_debug;
    public final Feature f_debugLevel;
    public final Feature f_function;
    public final Feature f_function_call;
    public final Feature f_safety;
    public final Feature f_array;
    public final Feature f_array_internalArray;
    public final Feature f_sys;
    public final Feature f_sys_array;
    public final Feature f_sys_array_length;
    public final Feature f_sys_array_data;
    Resolved(Feature universe)
    {
      this.universe = universe;
      t_i32           = Type.type(      "i32"         , universe);
      t_u32           = Type.type(      "u32"         , universe);
      t_i64           = Type.type(      "i64"         , universe);
      t_u64           = Type.type(      "u64"         , universe);
      t_ref_i32       = Type.type(true, "i32"         , universe);
      t_ref_u32       = Type.type(true, "u32"         , universe);
      t_ref_i64       = Type.type(true, "i64"         , universe);
      t_ref_u64       = Type.type(true, "u64"         , universe);
      t_bool          = Type.type(      "bool"        , universe);
      t_sys           = Type.type(      "sys"         , universe);
      t_string        = Type.type(      "string"      , universe);
      t_conststring   = Type.type(      "conststring" , universe);
      t_object        = Type.type(      "Object"      , universe);
      t_unit          = Type.type(      "unit"        , universe);
      t_void          = Type.type(      "void"        , universe);
      f_void          = universe.get("void");
      f_choice        = universe.get("choice");
      f_TRUE          = universe.get("TRUE");
      f_FALSE         = universe.get("FALSE");
      f_bool          = universe.get("bool");
      f_bool_NOT      = f_bool.get("prefix !");
      f_bool_AND      = f_bool.get("infix &&");
      f_bool_OR       = f_bool.get("infix ||");
      f_bool_IMPLIES  = f_bool.get("infix :");
      f_debug         = universe.get("debug", 0);
      f_debugLevel    = universe.get("debugLevel");
      f_function      = universe.get(FUNCTION_NAME);
      f_function_call = f_function.get("call");
      f_safety        = universe.get("safety");
      f_array         = universe.get("array", 1);
      f_array_internalArray = f_array.get("internalArray");
      f_sys                 = universe.get("sys");
      f_sys_array           = f_sys.get("array");
      f_sys_array_data      = f_sys_array.get("data");
      f_sys_array_length    = f_sys_array.get("length");
      resolved = this;
      t_ADDRESS  .resolveArtificialType(universe.get("Object"));
      t_UNDEFINED.resolveArtificialType(universe);
      t_ERROR    .resolveArtificialType(f_ERROR);
    }


    /**
     * Flag used to detect repeated calls to markInternallyUsed.
     */
    private boolean _doneInternallyUsed = false;

    /**
     * Mark internally used features as used.
     */
    void markInternallyUsed(Resolution res) {
      if (!_doneInternallyUsed)
        {
          _doneInternallyUsed = true;
          var tag = FuzionConstants.CHOICE_TAG_NAME;
          universe.get("i32",1).get("val").markUsed(res, SourcePosition.builtIn);
          universe.get("u32",1).get("val").markUsed(res, SourcePosition.builtIn);
          universe.get("i64",1).get("val").markUsed(res, SourcePosition.builtIn);
          universe.get("u64",1).get("val").markUsed(res, SourcePosition.builtIn);
          universe.get("bool").get(tag) .markUsed(res, SourcePosition.builtIn);
          universe.get("conststring")   .markUsed(res, SourcePosition.builtIn);
          universe.get("conststring").get("isEmpty").markUsed(res, SourcePosition.builtIn);  // NYI: check why this is not found automatically
          f_sys_array_data              .markUsed(res, SourcePosition.builtIn);
          f_sys_array_length            .markUsed(res, SourcePosition.builtIn);
          universe.get("unit")          .markUsed(res, SourcePosition.builtIn);
          universe.get("void")          .markUsed(res, SourcePosition.builtIn);
        }
    }


  }

  /*----------------------------  variables  ----------------------------*/


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Find the unique instance of t
   */
  public static Type intern(Type t)
  {
    if (PRECONDITIONS) require
      (t.isGenericArgument() || t.feature != null || Errors.count() > 0);

    if (!t.isGenericArgument())
      {
        t.outerInterned();
      }
    var tg = t._generics.listIterator();
    while (tg.hasNext())
      {
        tg.set(intern(tg.next()));
      }
    Type existing = t._interned;
    if (existing == null)
      {
        existing = types.get(t);
        if (existing == null)
          {
            types.put(t,t);
            existing = t;
          }
        t._interned = existing;
      }
    return existing;
  }


  /**
   * Return the total number of unique types stored globally.
   */
  public static int num()
  {
    return types.size();
  }

}
