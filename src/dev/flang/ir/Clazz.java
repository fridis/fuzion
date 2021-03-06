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
 * Source of class Clazz
 *
 *---------------------------------------------------------------------*/

package dev.flang.ir;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.ast.Assign; // NYI: remove dependency!
import dev.flang.ast.Box; // NYI: remove dependency!
import dev.flang.ast.Call; // NYI: remove dependency!
import dev.flang.ast.Case; // NYI: remove dependency!
import dev.flang.ast.Consts; // NYI: remove dependency!
import dev.flang.ast.Expr; // NYI: remove dependency!
import dev.flang.ast.FeErrors; // NYI: remove dependency!
import dev.flang.ast.Feature; // NYI: remove dependency!
import dev.flang.ast.FeatureVisitor; // NYI: remove dependency!
import dev.flang.ast.If; // NYI: remove dependency!
import dev.flang.ast.InitArray; // NYI: remove dependency!
import dev.flang.ast.Impl; // NYI: remove dependency!
import dev.flang.ast.Match; // NYI: remove dependency!
import dev.flang.ast.Tag; // NYI: remove dependency!
import dev.flang.ast.Type; // NYI: remove dependency!
import dev.flang.ast.Types; // NYI: remove dependency!
import dev.flang.ast.Unbox; // NYI: remove dependency!

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Clazz represents a runtime type, i.e, a Type with actual generic arguments.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Clazz extends ANY implements Comparable
{


  /*-----------------------------  statics  -----------------------------*/


  //  static int counter;  {counter++; if ((counter&(counter-1))==0) System.out.println("######################"+counter+" "+this.getClass()); }
  // { if ((counter&(counter-1))==0) Thread.dumpStack(); }


  /**
   * Empty array as a result for fields() if there are no fields.
   */
  static final Clazz[] NO_CLAZZES = new Clazz[0];


  /*-----------------------------  classes  -----------------------------*/


  /**
   * Enum to record that we have checked the layout of this clazz and to detect
   * recursive value fields during layout.
   */
  enum LayoutStatus
  {
    Before,
    During,
    After,
  }


  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  public final Type _type;


  /**
   *
   */
  public final Clazz _outer;


  public final Map<Feature, Clazz> clazzForField_ = new TreeMap<>();


  /**
   * Clazzes required during runtime. These are indexed by
   * Feature.getRuntimeClazzId and used to quickly find the actual class
   * depending on the actual generic parameters given in this class or its super
   * classes.
   */
  ArrayList<Object> _runtimeClazzes = new ArrayList<>();


  /**
   * Cached result of choiceGenerics(), only used if isChoice() and
   * !isChoiceOfOnlyRefs().
   */
  public ArrayList<Clazz> choiceGenerics_;


  /**
   * Flag that is set while the layout of objects of this clazz is determined.
   * This is used to detect recursive clazzes that contain value type fields of
   * the same type as the clazz itself.
   */
  LayoutStatus layouting_ = LayoutStatus.Before;


  /**
   * Will instances of this class be created?
   */
  public boolean isInstantiated_ = false;


  /**
   * Is this a normalized outer clazz? If so, there might be calls on this as an
   * outer clazz even if it is not instantiated.
   */
  public boolean isNormalized_ = false;


  /**
   * Is this clazz ever called?  Usually, this is the same as isInstantiated_,
   * except for instances created by intrinsics: These are created even for
   * clazzes that are not called.
   */
  public boolean isCalled_ = false;


  /**
   * Is there a non-dynamic call to this clazz?  If so, it has to be considered
   * called even if the outer clazz is not instantiated.  NYI: check why!
   */
  boolean _isCalledDirectly = false;


  /**
   * If instances of this class are created, this gives a source code position
   * that does create such an instance.  To be used in error messages.
   */
  SourcePosition instantiationPos_ = null;


  /**
   * In case abstract methods are called on this, this lists the abstract
   * methods that have been found to do so.
   */
  TreeSet<Feature> abstractCalled_ = null;


  /**
   * Set of all heirs of this clazz.
   */
  TreeSet<Clazz> _heirs = null;


  /**
   * The dynamic binding implementation used for this clazz. null if !isRef().
   */
  public DynamicBinding _dynamicBinding;


  /**
   * The type of the result of calling thiz clazz.
   *
   * This is initialized after Clazz creation by dependencies().
   */
  Clazz _resultClazz;


  /**
   * The result field of this routine if it exists.
   *
   * This is initialized after Clazz creation by dependencies().
   */
  Clazz _resultField;


  /**
   * The argument fields of this routine.
   *
   * This is initialized after Clazz creation by dependencies().
   */
  Clazz[] _argumentFields;


  /**
   * The actual generics of this clazz.
   *
   * This is initialized after Clazz creation by dependencies().
   */
  Clazz[] _actualGenerics;


  /**
   * If this clazz contains a direct outer ref field, this is the direct outer
   * ref. null otherwise.
   *
   * This is initialized after Clazz creation by dependencies().
   */
  Clazz _outerRef;


  /**
   * If this clazz is a choice clazz that requires a tag, this is the tag.  null
   * otherwise.
   *
   * This is initialized after Clazz creation by dependencies().
   */
  Clazz _choiceTag;


  /**
   * Fields in instances of this clazz. Set during layout phase.
   */
  Clazz[] _fields;


  /**
   * For a clazz with isRef()==true, this will be set to a value version of this
   * clazz.
   */
  private Clazz _asValue;


  /**
   * Any data the backend might wish to store with an instance of Clazz.
   */
  public Object _backendData;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param type
   *
   * @param outer
   */
  public Clazz(Type actualType, Clazz outer)
  {
    if (PRECONDITIONS) require
      (!Clazzes.closed,
       Errors.count() > 0 || !actualType.isGenericArgument(),
       Errors.count() > 0 || actualType.isFreeFromFormalGenerics(),
       actualType.featureOfType().outer() == null || outer.feature().inheritsFrom(actualType.featureOfType().outer()),
       Errors.count() > 0 || actualType.featureOfType().outer() != null || outer == null,
       Errors.count() > 0 || (actualType != Types.t_ERROR     &&
                              actualType != Types.t_UNDEFINED   ),
       outer == null || outer._type != Types.t_ADDRESS);

    if (actualType == Types.t_UNDEFINED)
      {
        actualType = Types.t_ERROR;
      }

    this._type = actualType;
    /* NYI: Handling of outer in Clazz is not done properly yet. There are two
     * basic cases:
     *
     * 1. outer is a value type
     *
     *    in this case, we specialize all inner clazzes for every single value
     *    type outer clazz.
     *
     * 2. outer is a reference type
     *
     *    in this case, we do not specialize inner clazzes, but can normalize
     *    the outer clazz using the outer feature of the inner clazz.  This
     *    means, say we have a feature 'pop() T' within a ref clazz 'stack<T>'.
     *    There exists a ref clazz 'intStack' that inherits from 'stack<i32>'.
     *    The clazz for 'intStack.pop' then should be 'stack<i32>.pop', this
     *    clazz can be shared with all other sub-clazzes of 'stack<i32>', but
     *    not with sub-clazzes with different actual generics.
     */
    this._outer = normalizeOuter(actualType, outer);

    if (isChoice())
      {
        choiceGenerics_ = choiceGenerics();
      }
    this._dynamicBinding = null;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create all the clazzes that this clazz depends on such as result type,
   * inner fields, etc.
   */
  void dependencies()
  {
    _resultClazz = determineResultClazz();
    _resultField = determineResultField();
    _argumentFields = determineArgumentFields();
    _actualGenerics = determineActualGenerics();
    _outerRef = determineOuterRef();
    _choiceTag = determineChoiceTag();
    _asValue = determineAsValue();
  }


  /**
   * Check if this clazz has an outer ref that is used.
   *
   * if an outer ref is used (i.e., state is resolved) to access the outer
   * instance, we must not normalize because we will need the exact type of the
   * outer instance to specialize code or to access features that only exist in
   * the specific version
   */
  private boolean hasUsedOuterRef()
  {
    var or = feature().outerRef_;
    return feature().hasResult()  // do not specialize a constructor
      && or != null && or.state().atLeast(Feature.State.RESOLVED);
  }


  /**
   * Normalize an outer clazz for a given type. For a reference clazz that
   * inherits from f, this will return the corresponding clazz derived from
   * f. The idea is that, e.g., we do not need to distinguish conststring.length
   * from array<u8>.length.
   *
   * @param t the type of the newly created clazz
   *
   * @param outer the outer clazz that should be normalized for the newly
   * created clazz
   *
   * @return the normalized version of outer.
   */
  private Clazz normalizeOuter(Type t, Clazz outer)
  {
    if (outer != null && !hasUsedOuterRef())
      {
        outer = outer.normalize(t.featureOfType().outer());
      }
    return outer;
  }


  /**
   * Normalize a reference clazz to the given feature.  For a reference clazz
   * that inherits from f, this will return the corresponding clazz derived
   * from f. The idea is that, e.g., we do not need to distinguish conststring.length
   * from array<u8>.length.
   *
   * @param f the feature we want to normalize to (array in the example above).
   *
   * @return the normalized clazz.
   */
  private Clazz normalize(Feature f)
  {
    if (// an outer clazz of value type is not normalized (except for
        // univers, which was done already).
        !isRef() ||

        // optimization: if feature() is already f, there is nothing to
        // normalize anymore
        feature() == f ||

        // if an outer ref is used (i.e., state is resolved) to access the
        // outer instance, we must not normalize because we will need the
        // exact type of the outer instance to specialize code or to access
        // features that only exist in the specific version
        hasUsedOuterRef()
        )
      {
        return this;
      }
    else
      {
        var t = actualType(f.thisType()).asRef();
        return normalize2(t);
      }
  }
  private Clazz normalize2(Type t)
  {
    var f = t.featureOfType();
    if (f == Types.resolved.universe)
      {
        return Clazzes.universe.get();
      }
    else
      {
        var normalized = Clazzes.create(t, normalize2(f.outer().thisType()));
        normalized.isNormalized_ = true;
        return normalized;
      }
  }


  /**
   * Make sure this clazz is added to the set of heirs for all of its parents.
   */
  void registerAsHeir()
  {
    if (isRef())
      {
        registerAsHeir(this);
      }
  }

  /**
   * private helper for registerAsHeir().  Make sure this clazz is added to the
   * set of heirs of parent ann all of parent's parents.
   */
  private void registerAsHeir(Clazz parent)
  {
    if (parent._heirs == null)
      {
        parent._heirs = new TreeSet<>();
      }
    parent._heirs.add(this);
    for (Call p: parent.feature().inherits)
      {
        var pc = parent.actualClazz(p.type().asRef());
        registerAsHeir(pc);
      }
  }


  /**
   * Set of heirs of this clazz, including this itself.  This is defined for
   * clazzes with isRef() only.
   *
   * @return the heirs including this.
   */
  public Set<Clazz> heirs()
  {
    if (PRECONDITIONS) require
      (isRef());

    return _heirs;
  }


  /**
   * Convert a given type to the actual type within this class. An
   * actual type does not refer to any formal generic arguments.
   */
  public Type actualType(Type t)
  {
    if (PRECONDITIONS) require
      (t != null,
       Errors.count() > 0 || !t.isOpenGeneric());

    t = this._type.actualType(t);
    if (this._outer != null)
      {
        t = this._outer.actualType(t);
      }
    return Types.intern(t);
  }


  /**
   * Convert a given type to the actual runtime clazz within this class. The
   * formal generics arguments will first be replaced via actualType(t), and the
   * Clazz will be created from the result.
   */
  public Clazz actualClazz(Type t)
  {
    if (PRECONDITIONS) require
      (t != null,
       Errors.count() > 0 || !t.isOpenGeneric());

    return Clazzes.clazz(actualType(t));
  }


  /**
   * Convert the given generics to the actual generics of this class.
   *
   * @param generics a list of generic arguments that might itself consist of
   * formal generics
   *
   * @return The list of actual generics after replacing the generics of this
   * class or its outer classes.
   */
  public List<Type> actualGenerics(List<Type> generics)
  {
    generics = this._type.replaceGenerics(generics);
    if (this._outer != null)
      {
        generics = this._outer.actualGenerics(generics);
      }
    return generics;
  }


  /**
   * The feature underlying this clazz.
   */
  public Feature feature()
  {
    return this._type.featureOfType();
  }


  /**
   * isRef
   */
  public boolean isRef()
  {
    return this._type.isRef();
  }


  /**
   * isUnitType checks if there exists only one single value in instances of
   * this clazz, so this value does not need to be stored.
   */
  public boolean isUnitType()
  {
    if (isRef() || feature().isBuiltInPrimitive() || isVoidType())
      {
        return false;
      }
    if (isChoice())
      {
        if (choiceTag() != null)
          {
            return false;
          }
        for (var cg : choiceGenerics())
          {
            if (!cg.isUnitType())
              {
                return false;
              }
          }
      }
    else
      {
        for (var f : fields())
          {
            if (!f.fieldClazz().isUnitType())
              {
                return false;
              }
          }
      }
    return true;
  }


  /**
   * isVoidType checks if this is void.  This is not true for user defined void
   * types, i.e., any product type, e.g.,
   *
   *    absurd (i i32, v void) is {}
   *
   * that has a field of type void is effectively a void type. This call will,
   * however, return false for ushc user defined void types.
   */
  public boolean isVoidType()
  {
    return _type == Types.resolved.t_void;
  }


  /**
   * Do we need f (or its redefinition) to be present in this clazz' dynamic binding data?
   */
  boolean isAddedToDynamicBinding(Feature f)
  {
    return
      isRef() &&
      Clazzes.isUsed(f, this) &&
      (f.isField() ||
       f.isCalledDynamically())
      ;
  }


  /**
   * Layout this clazz. In case a cyclic nesting of value fields is detected,
   * report an error.
   */
  void layoutAndHandleCycle()
  {
    List<SourcePosition> cycle = layout();
    if (cycle != null && Errors.count() <= IrErrors.count)
      {
        StringBuilder cycleString = new StringBuilder();
        for (SourcePosition p : cycle)
          {
            cycleString.append(p.show()).append("\n");
          }
        IrErrors.error(this._type.pos,
                       "Cyclic field nesting is not permitted",
                       "Cyclic value field nesting would result in infinitely large objects.\n" +
                       "Cycle of nesting found during clazz layout:\n" +
                       cycleString + "\n" +
                       "To solve this, you could change one or several of the fields involved to a reference type by adding 'ref' before the type.");
      }

    createDynamicBinding();
  }


  /**
   * layout this clazz.
   */
  private List<SourcePosition> layout()
  {
    List<SourcePosition> result = null;
    switch (layouting_)
      {
      case During: result = new List<>(this._type.pos); break;
      case Before:
        {
          layouting_ = LayoutStatus.During;
          if (isChoice())
            {
              for (Clazz c : choiceGenerics())
                {
                  if (result == null && !c.isRef())
                    {
                      result = c.layout();
                    }
                }
            }
          else if (feature().impl.kind_ != Impl.Kind.Intrinsic)
            {
              var fields = new ArrayList<Clazz>();
              for (var f: feature().allInnerAndInheritedFeatures())
                {
                  if (result == null &&
                      f.isField() &&
                      Clazzes.isUsed(f, this) &&
                      this != Clazzes.c_void.get() &&
                      !f.resultType().isOpenGeneric() &&
                      f == findRedefinition(f)  // NYI: proper field redefinition handling missing, see tests/redef_args/*
                      )
                    {
                      fields.add(lookup(f, Call.NO_GENERICS, f.isUsedAt()));
                      var fieldClazz = clazzForField(f);
                      if (!fieldClazz.isRef() &&
                          !fieldClazz.feature().isBuiltInPrimitive() &&
                          !fieldClazz.isVoidType())
                        {
                          result = fieldClazz.layout();
                          if (result != null)
                            {
                              result.add(f.pos);
                              result.add(this._type.pos);
                            }
                        }
                    }
                }
              _fields = fields.toArray(new Clazz[fields.size()]);
            }
          layouting_ = LayoutStatus.After;
        }
      case After: break;
      }
    return result;
  }


  /**
   * Create dynamic binding data for this clazz in case it is a ref.
   */
  private void createDynamicBinding()
  {
    if (isRef() && _dynamicBinding == null)
      {
        this._dynamicBinding = new DynamicBinding(this);

        // NYI: Inheritance must be done differently: We should
        // (recursively) traverse all parents and hand down features from
        // each parent to this clazz to find the actual FeatureName of the
        // feature after inheritance. For each parent for which the feature
        // is called, we need to set up a table in this tree that contains
        // the inherited feature or its redefinition.
        for (Feature f: feature().allInnerAndInheritedFeatures())
          {
            // if (isInstantiated_) -- NYI: if not instantiated, we do not need to add f to dynamic binding, but we seem to need its side-effects
            if (isAddedToDynamicBinding(f))
              {
                if (f.isCalledDynamically() &&
                    Clazzes.isCalledDynamically(f) &&
                    this._type != Types.t_ADDRESS /* NYI: better something like this.isInstantiated() */
                    )
                  {
                    var innerClazz = lookup(f, Call.NO_GENERICS, f.isUsedAt());
                    var callable = Clazzes._backend_.callable(false, innerClazz, this);
                    _dynamicBinding.addCallable(f, callable);
                    _dynamicBinding.add(f, innerClazz, this);
                  }
              }
          }
      }
  }


  /**
   * find redefinition of a given feature in this clazz. NYI: This will have to
   * take the whole inheritance chain into account and the parent view that is
   * being filled with live into account:
   */
  private Feature findRedefinition(Feature f)
  {
    var fn = f.featureName();
    var tf = feature();
    if (tf != Types.f_ERROR && f != Types.f_ERROR && tf != Types.resolved.f_void)
      {
        var chain = tf.findInheritanceChain(f.outer());
        check
          (chain != null || Errors.count() > 0);
        if (chain != null)
          {
            for (var p: chain)
              {
                fn = f.outer().handDown(f, fn, p, feature());  // NYI: need to update f/f.outer() to support several levels of inheritance correctly!
              }
          }
      }
    return feature().findDeclaredOrInheritedFeature(fn);
  }


  /**
   * Lookup the code to call the feature f from this clazz using dynamic binding
   * if needed.
   *
   * This is not intended for use at runtime, but during analysis of static
   * types or to fill the virtual call table.
   *
   * @param f the feature that is called
   *
   * @param actualGenerics the actual generics provided in the call,
   * Call.NO_GENERICS if none.
   *
   * @param p if this lookup would result in the returned feature to be called,
   * p gives the position in the source code that causes this call.  p must be
   * null if the lookup does not causes a call, but it just done to determine
   * the type.
   *
   * @return the inner clazz of the target in the call.
   */
  public /* NYI: make package private */ Clazz lookup(Feature f,
                      List<Type> actualGenerics,
                      SourcePosition p)
  {
    if (PRECONDITIONS) require
      (f != null,
       this != Clazzes.c_void.get());

    return lookup(f, actualGenerics, p, false);
  }


  /**
   * Lookup the code to call the feature f from this clazz using dynamic binding
   * if needed.
   *
   * This is not intended for use at runtime, but during analysis of static
   * types or to fill the virtual call table.
   *
   * @param f the feature that is called
   *
   * @param actualGenerics the actual generics provided in the call,
   * Call.NO_GENERICS if none.
   *
   * @param p if this lookup would result in the returned feature to be called,
   * p gives the position in the source code that causes this call.  p must be
   * null if the lookup does not causes a call, but it just done to determine
   * the type.
   *
   * @param isInstantiated true iff this is a call in an inheritance clause.  In
   * this case, the result clazz will not be marked as instantiated since the
   * call will work on the instance of the inheriting clazz.
   *
   * @return the inner clazz of the target in the call.
   */
  Clazz lookup(Feature f,
               List<Type> actualGenerics,
               SourcePosition p,
               boolean isInheritanceCall)
  {
    if (PRECONDITIONS) require
      (f != null,
       this != Clazzes.c_void.get());

    Feature af = findRedefinition(f);
    check
      (Errors.count() > 0 || af != null);

    Clazz innerClazz = null;
    if (f == Types.f_ERROR || af == null)
      {
        innerClazz = Clazzes.error.get();
      }
    else
      {
        if (af.impl == Impl.ABSTRACT)
          {
            if (abstractCalled_ == null)
              {
                abstractCalled_ = new TreeSet<>();
              }
            abstractCalled_.add(af);
          }

        Type t = af.thisType().actualType(af, actualGenerics);
        t = actualType(t);
        innerClazz = Clazzes.clazzWithSpecificOuter(t, this);
        if (p != null)
          {
            if (!isInheritanceCall)
              {
                innerClazz.called(p);
                innerClazz.instantiated(p);
              }
          }
        check
          (innerClazz._type == Types.t_ERROR || innerClazz._type.featureOfType() == af);
      }

    if (POSTCONDITIONS) ensure
      (Errors.count() > 0 || af == null || innerClazz._type != Types.t_ERROR,
       innerClazz != null);

    return innerClazz;
  }


  /**
   * Get the runtime clazz of a field in this clazz.
   *
   * NYI: try to remove
   *
   * @param field a field
   */
  public Clazz clazzForField(Feature field)
  {
    check
      (field.isField(),
       feature().inheritsFrom(field.outer()));

    var result = clazzForField_.get(field);
    if (result == null)
      {
        result =
          field.isOuterRef() && field.outer().isOuterRefAdrOfValue()     ? actualClazz(Types.t_ADDRESS) :
          field.isOuterRef() && field.outer().isOuterRefCopyOfValue() ||
          !field.isOuterRef() && field != field.outer().resultField() // NYI: use lookup/resultClazz for all fields
                                                                         ? actualClazz(field.resultType())
                                                                         : lookup(field, Call.NO_GENERICS, field.isUsedAt()).resultClazz();
        clazzForField_.put(field, result);
      }
    return result;
  }


  /**
   * Special handling for field results clazzes that differ for some outer ref
   * fields.
   *
   * NYI: Try to remove this special handling.
   */
  public Clazz fieldClazz()
  {
    if (PRECONDITIONS) require
      (feature().isField());

    var field = feature();

    return
      field.isOuterRef() && field.outer().isOuterRefAdrOfValue()  ? actualClazz(Types.t_ADDRESS) :
      field.isOuterRef() && field.outer().isOuterRefCopyOfValue() ||
      !field.isOuterRef() && field != field.outer().resultField() ? actualClazz(field.resultType())
                                                                  : resultClazz();
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return
      (this._type == Types.t_ERROR   ||
       this._type == Types.t_ADDRESS ||
       this._outer == null              )
      ? this._type.toString() // error, address or universe
      : (""
         + ((this._outer == Clazzes.universe.get())
            ? ""
            : this._outer.toString() + ".")
         + (this.isRef()
            ? "ref "
            : ""
            )
         + feature().featureName().baseName() + (this._type._generics.isEmpty()
                                                 ? ""
                                                 : "<" + this._type._generics + ">"));
  }


  /**
   * toString
   *
   * @return
   */
  public String toString2()
  {
    return "CLAZZ:" + this._type + (this._outer != null ? " in " + this._outer : "");
  }


  /**
   * Check if a value of clazz other can be assigned to a field of this clazz.
   *
   * @other the value to be assigned to a field of type this
   *
   * @return true iff other can be assigned to a field of type this.
   */
  public boolean isAssignableFrom(Clazz other)
  {
    return (this==other) || isRef() && this._type.isAssignableFrom(other._type);
  }


  /**
   * Compare this to other for creating unique clazzes.
   */
  public int compareTo(Object other)
  {
    return compareTo((Clazz) other);
  }

  /**
   * Helper routine for compareTo: compare the outer classes.  If outer are refs
   * for both clazzes, they can be considered the same as long as their outer
   * classes (recursively) are the same. If they are values, they need to be
   * exactly equal.
   */
  private int compareOuter(Clazz other)
  {
    var to = this ._outer;
    var oo = other._outer;
    int result = 0;
    if (to != oo)
      {
        result =
          to == null ? -1 :
          oo == null ? +1 : 0;
        if (result == 0)
          {
            if (to.isRef() && oo.isRef())
              { // NYI: If outer is normalized for refs as descibed in the
                // constructor, there should be no need for special handling of
                // ref types here.
                result = to._type.compareToIgnoreOuter(oo._type);
                if (result == 0)
                  {
                    result = to.compareOuter(oo);
                  }
              }
            else
              {
                result =
                  !to.isRef() && !oo.isRef() ? to.compareTo(oo) :
                  to.isRef() ? +1
                             : -1;
              }
          }
      }
    return result;
  }


  /**
   * Compare this to other for creating unique clazzes.
   */
  public int compareTo(Clazz other)
  {
    if (PRECONDITIONS) require
      (other != null,
       this .getClass() == Clazz.class,
       other.getClass() == Clazz.class,
       this ._type == Types.intern(this ._type),
       other._type == Types.intern(other._type));

    var result = this._type.compareToIgnoreOuter(other._type);
    if (result == 0)
      {
        result = compareOuter(other);
      }
    return result;
  }


  public boolean dependsOnGenerics()  //  NYI: Only used in caching for Type.clazz, which should be removed
  {
    return !this._type._generics.isEmpty() || (this._outer != null && this._outer.dependsOnGenerics());
  }


  /**
   * Visitor to find all runtime classes.
   */
  private class FindClassesVisitor extends FeatureVisitor
  {
    public void      action     (Unbox      u, Feature outer) { Clazzes.findClazzes(u, Clazz.this); }
    public void      action     (Assign     a, Feature outer) { Clazzes.findClazzes(a, Clazz.this); }
    public void      action     (Box        b, Feature outer) { Clazzes.findClazzes(b, Clazz.this); }
    public void      actionAfter(Case       c, Feature outer) { Clazzes.findClazzes(c, Clazz.this); }
    public Call      action     (Call       c, Feature outer) { Clazzes.findClazzes(c, Clazz.this); return c; }
    public void      action     (If         i, Feature outer) { Clazzes.findClazzes(i, Clazz.this); }
    public InitArray action     (InitArray  i, Feature outer) { Clazzes.findClazzes(i, Clazz.this); return i; }
    public void      action     (Match      m, Feature outer) { Clazzes.findClazzes(m, Clazz.this); }
    public void      action     (Tag        t, Feature outer) { Clazzes.findClazzes(t, Clazz.this); }
    void visitAncestors(Feature f)
    {
      f.visit(this);
      for (Call c: f.inherits)
        {
          Feature cf = c.calledFeature();
          var n = c._actuals.size();
          for (var i = 0; i < n; i++)
            {
              var a = c._actuals.get(i);
              if (i >= cf.arguments.size())
                {
                  check
                    (Errors.count() > 0);
                }
              else
                {
                  var cfa = cf.arguments.get(i);
                  var ccc = lookup(cfa, Call.NO_GENERICS, f.isUsedAt());
                  if (c.parentCallArgFieldIds_ < 0)
                    {
                      c.parentCallArgFieldIds_ = Clazz.this.feature().getRuntimeClazzIds(n);
                    }
                  Clazz.this.setRuntimeData(c.parentCallArgFieldIds_+i, ccc);
                }
            }

          check
            (Errors.count() > 0 || cf != null);

          if (cf != null)
            {
              visitAncestors(cf);
            }
        }
    }
  }


  /**
   * Find all clazzes referenced by this even if this is not executed.
   */
  void findAllClasses()
  {
  }


  /**
   * Find all inner clazzes of this that are referenced when this is executed
   */
  void findAllClassesWhenCalled()
  {
    var f = feature();
    new FindClassesVisitor().visitAncestors(f);
    for (Feature ff: f.allInnerAndInheritedFeatures())
      {
        if (Clazzes.isUsed(ff, this) &&
            this._type != Types.t_ADDRESS // NYI: would be better is isUSED would return false for ADDRESS
            && isAddedToDynamicBinding(ff))
          {
            Clazzes.whenCalledDynamically(ff,
                                          () -> { var innerClazz = lookup(ff, Call.NO_GENERICS, ff.isUsedAt()); });
          }
      }
  }


  /**
   * Find all clazzes that are created when f is called on this clazz.
   *
   * This determines all the possible runtime types of all calls within the code
   * of f and within the code of all clazzes f inherits from.
   *
   * @param f the feature that is called on this.
   */
  void findAllClasses(Expr target, Feature f)
  {
    target.visit(new FindClassesVisitor(), f);
  }


  /**
   * During findClazzes, store data for a given id.
   *
   * @param id the id obtained via Feature.getRuntimeClazzId()
   *
   * @param data the data to be stored for this id.
   */
  public void setRuntimeData(int id, Object data)
  {
    if (PRECONDITIONS) require
      (id >= 0,
       id < feature().runtimeClazzIdCount());

    int cnt = feature().runtimeClazzIdCount();
    this._runtimeClazzes.ensureCapacity(cnt);
    while (this._runtimeClazzes.size() < cnt)
      {
        this._runtimeClazzes.add(null);
      }
    this._runtimeClazzes.set(id, data);

    if (POSTCONDITIONS) ensure
      (getRuntimeData(id) == data);
  }


  /**
   * During execution, retrieve the data stored for given id.
   *
   * @param id the id used in setRuntimeData
   *
   * @return the data stored for this id.
   */
  public Object getRuntimeData(int id)
  {
    if (PRECONDITIONS) require
      (id < feature().runtimeClazzIdCount(),
       id >= 0);

    return this._runtimeClazzes.get(id);
  }


  /**
   * During findClazzes, store a clazz for a given id.
   *
   * @param id the id obtained via Feature.getRuntimeClazzId()
   *
   * @param cl the clazz to be stored for this id.
   */
  public void setRuntimeClazz(int id, Clazz cl)
  {
    if (PRECONDITIONS) require
      (id >= 0,
       id < feature().runtimeClazzIdCount());

    setRuntimeData(id, cl);

    if (POSTCONDITIONS) ensure
      (getRuntimeClazz(id) == cl);
  }


  /**
   * During execution, retrieve the clazz stored for given id.
   *
   * @param id the id used in setRuntimeClazz
   *
   * @return the clazz stored for this id.
   */
  public Clazz getRuntimeClazz(int id)
  {
    if (PRECONDITIONS) require
      (id < feature().runtimeClazzIdCount());

    return (Clazz) getRuntimeData(id);
  }


  /**
   * Is this a choice-type, i.e., does it directly inherit from choice?
   */
  public boolean isChoice()
  {
    return feature().isChoice();
  }


  /**
   * Is this a choice-type, i.e., does it directly inherit from choice?
   */
  public boolean isRoutine()
  {
    switch (feature().impl.kind_)
      {
      case Routine    :
      case RoutineDef : return true;
      default         : return false;
      }
  }


  /**
   * Is this a choice-type whose actual generics inlude ref?  If so, a field for
   * all the refs will be needed.
   */
  public boolean isChoiceWithRefs()
  {
    boolean hasRefs = false;

    if (choiceGenerics_ != null)
      {
        for (Clazz c : choiceGenerics_)
          {
            hasRefs = hasRefs || c.isRef();
          }
      }

    return hasRefs;
  }


  /**
   * Is this a choice-type whose actual generics are all refs or stateless
   * values? If so, no tag will be added, but ChoiceIdAsRef can be used.
   *
   * In case this is a choice of stateless value without any references, the
   * result will be false since in this case, it is better to use the an integer
   * stored in the tag.
   */
  public boolean isChoiceOfOnlyRefs()
  {
    boolean hasNonRefsWithState = false;

    if (choiceGenerics_ != null)
      {
        for (Clazz c : choiceGenerics_)
          {
            hasNonRefsWithState = hasNonRefsWithState || (!c.isRef() && !c.isUnitType() && !c.isVoidType());
          }
      }

    return isChoiceWithRefs() && !hasNonRefsWithState;
  }


  /**
   * Obtain the actual classes of a choice.
   *
   * @return the actual clazzes of this choice clazz, in the order they appear
   * as actual generics.
   */
  public ArrayList<Clazz> choiceGenerics()
  {
    if (PRECONDITIONS) require
      (isChoice());

    ArrayList<Clazz> result = new ArrayList<>();
    for (Type t : actualGenerics(feature().choiceGenerics()))
      {
        result.add(actualClazz(t));
      }

    return result;
  }


  /**
   * Determine the index of the generic argument of this choice type that
   * matches the given static type.
   */
  public int getChoiceTag(Type staticTypeOfValue)
  {
    if (PRECONDITIONS) require
      (isChoice(),
       staticTypeOfValue.isFreeFromFormalGenerics(),
       staticTypeOfValue == Types.intern(staticTypeOfValue));

    int result = -1;
    int index = 0;
    for (Clazz g : choiceGenerics_)
      {
        if (g._type.isAssignableFrom(staticTypeOfValue))
          {
            check
              (result < 0);
            result = index;
          }
        index++;
      }
    check
      (result >= 0);

    return result;
  }

  /**
   * For a choice clazz, get the clazz that corresponds to the generic
   * argument to choice at index id (0..n-1).
   *
   * @param id the index of the paramenter
   */
  public Clazz getChoiceClazz(int id)
  {
    if (PRECONDITIONS) require
      (isChoice(),
       id >= 0,
       id <  choiceGenerics_.size());

    return choiceGenerics_.get(id);
  }


  /**
   * Mark this as called at given source code position.
   *
   * @param at gives the position in the source code that causes this instantiation.  p can be
   * null, which means that this should not be marked as called.
   */
  void called(SourcePosition at)
  {
    if (PRECONDITIONS) require
      (Errors.count() > 0 || !isChoice());

    if (at != null &&
        (_outer == null || !_outer.isVoidType()) &&
        (Arrays.stream(argumentFields()).filter(a -> a.resultClazz().isVoidType()).findAny().isEmpty()) &&
        !isCalled_)
      {
        isCalled_ = true;

        if (isCalled())
          {
            var l = Clazzes._whenCalled_.remove(this);
            if (l != null)
              {
                for (var r : l)
                  {
                    r.run();
                  }
              }
          }

        if (feature().impl.kind_ == Impl.Kind.Intrinsic)
          { // value instances returned from intrinsics are recored to be
            // instantiated.  (ref instances are excluded since returning, e.g.,
            // a 'ref string' does not mean that we really have an instance of
            // string, but more likely an instance of a heir of string).
            var rc = resultClazz();
            if (!rc.isRef())
              {
                rc.instantiated(at);
              }
          }
      }
  }


  /**
   * Mark this as instantiated at given source code position.
   *
   * @param at gives the position in the source code that causes this instantiation.
   */
  void instantiated(SourcePosition at)
  {
    if (PRECONDITIONS) require
      (at != null);

    if (!isInstantiated_)
      {
        isInstantiated_ = true;
        instantiationPos_ = at;
      }
  }


  /**
   * Is this clazz called?  This tests this.isCalled_ and isInstantiated().
   */
  public boolean isCalled()
  {
    return (isCalled_ && isOuterInstantiated() || _isCalledDirectly) && feature().impl.kind_ != Impl.Kind.Abstract
      || toString().equals("array<i32>.internalArray") // NYI: Hack workaround for conststring
      ;
  }


  /**
   * Check of _outer is instantiated.
   */
  private boolean isOuterInstantiated()
  {
    return _outer == null ||

      // NYI: Once Clazz.normalize() is implemented better, a clazz C has
      // to be considered instantiated if there is any clazz D that
      // normalize() would replace by C if it occurs as an outer clazz.
      _outer == Clazzes.object.getIfCreated() ||
      _outer == Clazzes.string.getIfCreated() ||

      _outer.isNormalized_ ||

      _outer.isInstantiated();
  }


  /**
   * Flag to detetect endless recursion between isInstantiated() and
   * isRefWithInstantiatedHeirs(). This may happen in a clazz that inherits from
   * its outer clazz.
   */
  private int _checkingInstantiatedHeirs = 0;


  /**
   * Helper for isInstantiated to check if outer clazz this is a ref and there
   * are heir clazzes of this that are refs and that are instantiated.
   *
   * @return true iff this is a ref and there exists a heir of this that is
   * instantiated.
   */
  private boolean isRefWithInstantiatedHeirs()
  {
    var result = false;
    if (isRef())
      {
        for (var h : heirs())
          {
            h._checkingInstantiatedHeirs++;
            result = result
              || h != this && h.isInstantiated();
            h._checkingInstantiatedHeirs--;
          }
      }
    return result;
  }


  /**
   * Is this clazz instantiated?  This tests this.isInstantiated_ and,
   * recursively, _outer.isInstantiated().
   */
  public boolean isInstantiated()
  {
    return this == Clazzes.constStringBytesArray ||
      this == Clazzes.conststring.get() ||
      _checkingInstantiatedHeirs>0 || (isOuterInstantiated() || isChoice() || _outer.isRefWithInstantiatedHeirs()) && isInstantiated_;
  }


  /**
   * Check if this and all its (potentially normalized) outer clazzes are instantiated.
   */
  public boolean allOutersInstantiated()
  {
    return isInstantiated() && (_outer == null || _outer.allOutersInstantiated2());
  }


  /**
   * Helper for allOutersInstantiated to check outers if they are either
   * instantiated directly or are refs that have instantiated heirs.
   */
  private boolean allOutersInstantiated2()
  {
    return (isInstantiated() || isRefWithInstantiatedHeirs()) && (_outer == null || _outer.allOutersInstantiated2());
  }


  /**
   * Perform checks on classes such as that an instantiated clazz is not the
   * target of any calls to abstract methods that are not implemented by this
   * clazz.
   */
  public void check()
  {
    if (isInstantiated() && abstractCalled_ != null)
      {
        FeErrors.abstractFeatureNotImplemented(feature(), abstractCalled_, instantiationPos_);
      }
  }


  /**
   * In case this is a Clazz of value type, create the corresponding reference clazz.
   */
  public Clazz asRef()
  {
    return isRef()
      ? this
      : Clazzes.create(_type.asRef(), _outer);
  }


  /**
   * Recursive helper function for to find the clazz for an outer ref from
   * an inherited feature.
   *
   * @param cf the feature corresponding to the outer reference
   *
   * @param f the feature of the target of the inheritance call
   *
   * @param result must be null on the first call. This is used during recursive
   * traversal to check that all results are equal in case several results are
   * found.
   *
   * @return the static clazz of this call to an outer ref cf.
   */
  private Clazz inheritedOuterRefClazz(Clazz outer, Expr target, Feature cf, Feature f, Clazz result)
  {
    if (PRECONDITIONS) require
      ((outer != null) != (target != null));

    if (f.outerRefOrNull() == cf)
      { // a "normal" outer ref for the outer clazz surrounding this instance or
        // (if in recursion) an inherited outer ref referring to the target of
        // the inherits call
        if (outer == null)
          {
            outer = Clazzes.clazz(target, this);
          }
        check
          (result == null || result == outer);

        result = outer;
      }
    else
      {
        for (Call p : f.inherits)
          {
            result = inheritedOuterRefClazz(null, p.target, cf, p.calledFeature(), result);
          }
      }
    return result;
  }


  /**
   * Determine the clazz of the result of calling this clazz, cache the result.
   *
   * @return the result clazz.
   */
  public Clazz resultClazz()
  {
    return _resultClazz;
  }


  /**
   * Determine the clazz of the result of calling this clazz.
   *
   * @return the result clazz.
   */
  private Clazz determineResultClazz()
  {
    var f = feature();

    if (f.isUniverse() || f.returnType.isConstructorType())
      {
        return this;
      }
    else if (f.isOuterRef())
      {
        return _outer.inheritedOuterRefClazz(_outer._outer, null, f, _outer.feature(), null);
      }
    else
      {
        var t = actualType(f.resultType());
        if (t.isFreeFromFormalGenerics() && !t.isGenericArgument())
          {
            /* We have this situation:

               a is
                 b is
                   c is
                     f t.u.v.w.x.y.z
                 t is
                   u is
                     v is
                       w is
                         x is
                           y is
                             z is

                p is
                  q is
                    r : a is

                p.q.r.b.c.f

               so f.depth is 4 (a.b.c.f),
               t.featureOfType().depth() is 8 (a.t.u.v.w.x.y.z),
               inner.depth is 6 (p.q.r.b.c.f) and
               depthInSource is 7 (t.u.v.w.x.y.z). We have to
               go back 3 (6-4+1) levels in inner, i.e,. p.q.r.b.c.f -> p.q.r.*,
               and 7 levels in t (a.t.u.v.w.x.y.z -> *.t.u.v.w.x.y.z) to rebase t
               to become p.q.r.t.u.v.w.x.y.z.

               f:                       a.b.c.f
               t:                       a.t,u.v.w.x.y.z
               inner:                   p.q.r.b.c.f
               depthInSource              t.u.v.w.x.y.z
               back 3:                  p.q.r.*
               depthInSource part of t: *.t.u.v.w.x.y.z
               plugged together:        p.q.r.t.u.v.w.x.y.z

             */
            /* NYI: This implementation currently ignores depthInSource that could be determined via
               ((dev.flang.ast.FunctionReturnType) f.returnType).depthInSource (more complicated when
               type inference is used). We need proper tests for this and implement it for
               depthInSource > 1.
             */
            int goBack = f.depth()-t.featureOfType().depth() + 1;
            var innerBase = this;
            while (goBack > 0)
              {
                innerBase = innerBase._outer;
                goBack--;
              }
            if (t.featureOfType().outer() == null || innerBase.feature().inheritsFrom(t.featureOfType().outer()))
              {
                var res = innerBase == null ? Clazzes.create(t, null)
                  : innerBase.lookup(t.featureOfType(), t._generics, null);
                if (t.isRef())
                  {
                    res = res.asRef();
                  }
                return res;
              }
            else
              {
                // NYI: This branch should never be taken when rebasing above is implemented correctly.
                if (f.impl.kind_ == Impl.Kind.FieldDef)
                  {
                    return Clazzes.clazz(f.impl.initialValue(), this._outer);
                  }
                else if (f.impl.kind_ == Impl.Kind.RoutineDef)
                  {
                    /* NYI: Do we need special handling for inferred routine result as well?
                     *
                     *   return Clazzes.clazz(f.impl.initialValue, this._outer);
                     */
                  }
                return actualClazz(t);
              }
          }
        else
          {
            return actualClazz(t);
          }
      }
  }


  /**
   * Get the result field of this routine if it exists.
   *
   * @return the result field or null.
   */
  public Clazz resultField()
  {
    return _resultField;
  }


  /**
   * Determine the result field of this routine if it exists.
   *
   * @return the result field or null.
   */
  private Clazz determineResultField()
  {
    var f = feature();
    var r = f.resultField();
    return r == null
      ? null
      : lookup(r, Call.NO_GENERICS, r.isUsedAt());
  }


  /**
   * Get the argument fields of this routine
   *
   * @return the argument fields.
   */
  public Clazz[] argumentFields()
  {
    return _argumentFields;
  }


  /**
   * Determine the argument fields of this routine.
   *
   * @return the argument fields array or null if this is not a routine.
   */
  private Clazz[] determineArgumentFields()
  {
    Clazz[] result = NO_CLAZZES;
    var f = feature();
    switch (f.impl.kind_)
      {
      case Abstract  :
      case Intrinsic :
      case Routine   :
      case RoutineDef:
        {
          var args = new ArrayList<Clazz>();
          for (var a :f.arguments)
            {
              if (Clazzes.isUsed(a, this))
                {
                  if (a.isOpenGenericField())
                    {
                      for (var i = 0; i < a.selectSize(); i++)
                        {
                          var s = a.select(i);
                          if (Clazzes.isUsed(s, this))
                            {
                              var sa = lookup(s, Call.NO_GENERICS, s.isUsedAt());
                              if (sa.resultClazz()._type != Types.resolved.t_unit)  // NYI: Use a different, artificial type to mark unused open generic args!
                                {
                                  args.add(sa);
                                }
                            }
                        }
                    }
                  else if (this != Clazzes.c_void.get())
                    {
                      args.add(lookup(a, Call.NO_GENERICS, a.isUsedAt()));
                    }
                }
            }
          result = args.size() == 0 ? NO_CLAZZES : args.toArray(new Clazz[args.size()]);
          break;
        }
      }
    return result;
  }


  /**
   * Get the actual generic arguments of this clazz
   *
   * @return the actual generics
   */
  public Clazz[] actualGenerics()
  {
    return _actualGenerics;
  }


  /**
   * Determine the actual generic arguments of this clazz
   *
   * @return the actual generic argument
   */
  private Clazz[] determineActualGenerics()
  {
    var result = NO_CLAZZES;
    var gs = _type._generics;
    if (!gs.isEmpty())
      {
        result = new Clazz[gs.size()];
        for (int i = 0; i < gs.size(); i++)
          {
            result[i] = actualClazz(gs.get(i));
          }
      }
    return result;
  }


  /**
   * If this clazz contains a direct outer ref field, this is the direct outer
   * ref. null otherwise.
   */
  public Clazz outerRef()
  {
    return _outerRef;
  }


  /**
   * Determine the clazz of this clazz' direct outer ref field if it exists.
   *
   * @return the direct outer ref field, null if none.
   */
  private Clazz determineOuterRef()
  {
    Clazz result = null;
    var f = feature();
    switch (f.impl.kind_)
      {
      case Intrinsic  :
      case Routine    :
      case RoutineDef :
        {
          var or = f.outerRefOrNull();
          if (or != null && or.isUsed())
            {
              result = lookup(or, Call.NO_GENERICS, or.isUsedAt());
            }
          break;
        }
      }
    return result;
  }


  /**
   * If this clazz is a choice clazz that requires a tag, this will return the
   * tag.  null otherwise.
   */
  public Clazz choiceTag()
  {
    return _choiceTag;
  }


  /**
   * If this is a choice clazz that requires a tag, determine the clazz of this
   * tag field.
   *
   * @return the tag field, or null if this is not a choice or this is a choice
   * that does not need a tag.
   */
  private Clazz determineChoiceTag()
  {
    Clazz result = null;
    if (isChoice() && !isChoiceOfOnlyRefs())
      {
        var f = feature();
        result = lookup(f.choiceTag_, Call.NO_GENERICS, f.pos());
      }
    return result;
  }


  /**
   * Set of fields in this clazz, including inherited and artificially added fields.
   *
   * @return the set of fields, empty array if none. null before this clazz was
   * layouted or for a clazz that cannot be instantiated (instrinsic, abstract,
   * field, etc.).
   */
  public Clazz[] fields()
  {
    return isRef() || _fields == null ? NO_CLAZZES : _fields;
  }


  /**
   * For a field, determine its index in _outer.fields().
   *
   * @return index of this in fields()
   */
  public int fieldIndex()
  {
    if (PRECONDITIONS) require
      (feature().isField());

    int i = 0;
    for (var f : _outer._fields)
      {
        if (f == this)
          {
            return i;
          }
        i++;
      }
    throw new Error("Clazz.fieldIndex() did not find field");
  }


  /**
   * For a clazz with isRef()==true, determine a value version of this clazz.
   * Returns this if it is already a value or ADDRESS.
   */
  private Clazz determineAsValue()
  {
    return isRef() && _type != Types.t_ADDRESS
      ? Clazzes.create(_type.asValue(), _outer)
      : this;
  }


  /**
   * For a clazz with isRef()==true, return a value version of this clazz.
   * Returns this if it is already a value or ADDRESS.
   */
  public Clazz asValue()
  {
    return _asValue;
  }

}

/* end of file */
