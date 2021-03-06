;; The contents of this file are subject to the LGPL License, Version 3.0.

;; Copyright (C) 2012, 2013, Newcastle University

;; This program is free software: you can redistribute it and/or modify
;; it under the terms of the GNU Lesser General Public License as published by
;; the Free Software Foundation, either version 3 of the License, or
;; (at your option) any later version.

;; This program is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU Lesser General Public License for more details.

;; You should have received a copy of the GNU Lesser General Public License
;; along with this program.  If not, see http://www.gnu.org/licenses/.
(ns
    ^{:doc "Renders OWL Entities into tawny.owl forms"
      :author "Phillip Lord"}
  tawny.render
  (:require [tawny.owl :as owl]
            [tawny.lookup]
            [tawny.util])
  (:import
           (java.util Set)
           (org.semanticweb.owlapi.model
            OWLAnnotation
            OWLAnnotationProperty
            OWLAnnotationValue
            OWLClass
            OWLDataAllValuesFrom
            OWLDataComplementOf
            OWLDataExactCardinality
            OWLDataHasValue
            OWLDataMaxCardinality
            OWLDataMinCardinality
            OWLDataProperty
            OWLDataSomeValuesFrom
            OWLDatatypeRestriction
            OWLFacetRestriction
            OWLIndividual
            OWLLiteral
            OWLNamedIndividual
            OWLNamedObject
            OWLObjectAllValuesFrom
            OWLObjectComplementOf
            OWLObjectExactCardinality
            OWLObjectHasSelf
            OWLObjectHasValue
            OWLObjectIntersectionOf
            OWLObjectInverseOf
            OWLObjectMaxCardinality
            OWLObjectMinCardinality
            OWLObjectOneOf
            OWLObjectSomeValuesFrom
            OWLObjectUnionOf
            OWLObjectProperty
            OWLOntology
            OWLProperty
            )
           (org.semanticweb.owlapi.vocab
            OWLFacet
            OWL2Datatype)
           ))


(def
  ^{
    :dynamic true
    :doc "Strategy for determining action on reaching a terminal.
:resolve means translate into the clojure symbol.2
:object means leave as a Java object
A set means recursively render the object unless it is the set."}
  *terminal-strategy*
  :resolve
  )

(def
  ^{
    :dynamic true
    :doc "Use object/data explicit forms of functions, rather than trying to
infer. For example, use data-some or object-some, rather than owl-some." }
  *explicit*
  false)


(defmacro
  ^{:private true
    :doc "If *explicit* is true return the first form (which should be the
symbol for the interning version), else return the second (which should be the
non-interning equivalent)."}
  exp
  [a b]
  `(if *explicit* '~b '~a))

(defn named-entity-as-string
  "Return a string identifier for an entity"
  [^OWLNamedObject entity]
  (-> entity
      (.getIRI)
      (.toURI)
      (.toString)))

(defn ^java.util.Set ontologies
  "Fetch all known ontologies."
  []
  (.getOntologies (owl/owl-ontology-manager)))

(defn setmap
  "Apply f to list c, union the results."
  [f c]
  (apply clojure.set/union (map f c)))

(declare form)

(defmulti as-form
  "Render one of the main OWLEntities as one of the main functions
in tawny." class)

(defmethod as-form OWLClass
  [^OWLClass c]
  (let [ont (ontologies)
        super (.getSuperClasses c ont)
        equiv (.getEquivalentClasses c ont)
        disjoint (.getDisjointClasses c ont)
        annotation
        (setmap
         #(.getAnnotations c %) ont)
        cls (form c)
        ]
    `(
      ;; seems like a nice idea, but cls is always a symbol because form
      ;; OWLClass makes it so. Resolve-entity always returns a string, but
      ;; we don't know what kind -- a var string or an IRI?
      ~(if (symbol? cls)
         'defclass
         'owl-class)


      ~(form c)
      ~@(when (pos? (count super))
          (cons
           :super
           (form super)))
      ~@(when (pos? (count equiv))
          (cons
           :equivalent
           (form equiv)))
      ~@(when (pos? (count disjoint))
          (cons
           :disjoint
           (form disjoint)))
      ~@(when (pos? (count annotation))
          (cons :annotation
                (form annotation)))
      )))

(defmethod as-form OWLObjectProperty
  [^OWLObjectProperty p]
  (let [ont (ontologies)
        domain (.getDomains p ont)
        range (.getRanges p ont)
        inverseof (.getInverses p ont)
        superprop (.getSuperProperties p ont)
        characteristic
        (filter identity
                (list
                 (and
                  (.isTransitive p ont)
                  :transitive)
                 (and
                  (.isFunctional p ont)
                  :functional)
                 (and
                  (.isInverseFunctional p ont)
                  :inversefunctional)
                 (and
                  (.isSymmetric p ont)
                  :symmetric)
                 (and
                  (.isAsymmetric p ont)
                  :asymmetric)
                 (and
                  (.isIrreflexive p ont)
                  :irreflexive)
                 (and
                  (.isReflexive p ont)
                  :reflexive)
                 ))
        prop (form p)]

    `(
      ~(if (symbol? prop)
         'defoproperty
         'object-property)
      ~prop
      ~@(when (pos? (count superprop))
          (cons :super
                (form superprop)))
      ~@(when (pos? (count domain))
          (cons :domain
                (form domain)))
      ~@(when (pos? (count range))
          (cons :range
                (form range)))
      ~@(when (pos? (count inverseof))
          (cons :inverse
                (form inverseof)))
      ~@(when (pos? (count characteristic))
          (cons :characteristic
                characteristic)))))

(defmethod as-form OWLNamedIndividual
  [^OWLNamedIndividual p]
  (let [ont (ontologies)
        types (.getTypes p ont)
        same (setmap #(.getSameIndividuals p %) ont)
        diff (setmap #(.getDifferentIndividuals p %) ont)
        annotation
        (setmap
         (fn [^OWLOntology o] (.getAnnotations p o)) ont)
        fact
        (merge
         (into {} (setmap #(.getDataPropertyValues p %) ont))
         (into {} (setmap #(.getObjectPropertyValues p %) ont)))
        factnot
        (merge
         (into {} (setmap #(.getNegativeDataPropertyValues p %) ont))
         (into {} (setmap #(.getNegativeObjectPropertyValues p %) ont)))
        ind (form p)
        ]
    `(~(if (symbol? ind)
         'defindividual
         'individual)
      ~(form p)
      ~@(when (pos? (count types))
          (cons :type
                (form types)))
      ~@(when (pos? (count same))
          (cons :same
                (form same)))
      ~@(when (pos? (count diff))
          (cons :different
                (form diff)))
      ~@(when (pos? (count annotation))
          (cons :annotation
                (form annotation)))
      ~@(when (some
               #(pos? (count %))
               [fact factnot])
          (doall (concat
                  [:fact]
                  (form [:fact fact])
                  (form [:fact-not factnot])))))))

(defmethod as-form OWLDataProperty
  [^OWLDataProperty p]
  (let [ont (ontologies)
        domain (.getDomains p ont)
        range (.getRanges p ont)
        superprop (.getSuperProperties p ont)
        characteristic
        (filter identity
                (list
                 (and
                  (.isFunctional p ont)
                  :functional)
                 ))
        prop (form p)]

    `(
      ~(if (symbol? prop)
         'defdproperty
         'datatype-property)
      ~prop
      ~@(when (pos? (count superprop))
          (cons :super
                (form superprop)))
      ~@(when (pos? (count domain))
          (cons :domain
                (form domain)))
      ~@(when (pos? (count range))
          (cons :range
                (form range)))
      ~@(when (pos? (count characteristic))
          (cons :characteristic
                characteristic)))))

(defmethod as-form OWLAnnotationProperty
  [^OWLAnnotationProperty p]
  (let [ont (ontologies)
        super
        (setmap (fn [^OWLOntology o] (.getSuperProperties p o)) ont)
        ann
        (setmap #(.getAnnotations p %) ont)
        prop (form p)]
    `(
      ~(if (symbol? prop)
         'defaproperty
         'annotation-property)
      ~(form p)
      ~@(when (pos? (count super))
          (cons :super
                (form super)))
      ~@(when (pos? (count ann))
          (cons :annotation
                (form ann))))))

(defmethod as-form org.semanticweb.owlapi.model.OWLDatatype [_]
  ;; I think we can safely ignore this here -- if it declared, then it should
  ;; be used elsewhere also. I think. Regardless, there is no read syntax in
  ;; tawny at the moment.
  )

(defmethod as-form :default [_]
  (println "Unknown element in signature")
  (Thread/dumpStack)
  '(unknown as-form))


(defmulti form
  "Render any OWLEntity or collections containing these entities as Clojure
forms."
  class)

;; how to get from {:a {1 2}} {:b {3 4}}
;; to [:a 1][:a 2]
;; or support (fact I1 I2)?

(defmethod form clojure.lang.IPersistentVector [v]
  (let [f (symbol (name (first v)))]
    (for [[ope ind]
          (reduce
           concat
           (for [[k v] (second v)]
             (for [x v]
               [k x])))]
      `(~f ~(form ope) ~(form ind)))))

(defmethod form clojure.lang.ISeq [s]
  (map form s))

(defmethod form Set [s]
  ;; no lazy -- we are going to render the entire form anyway, and we are
  ;; using a dynamic binding to cache the iri-to-var map. Lazy eval will break
  ;; this big time.
  (tawny.util/domap form s))

(defmethod form java.util.Map [m]
  (tawny.util/dofor
   [[k v] m]
   `(~(form k) ~(form v))))

(defn- entity-or-iri
  "Return either the interned var holding an entity, or an IRI,
depending on the value of *terminal-strategy*"
  [c]
  (case *terminal-strategy*
    :resolve
    (let [res (tawny.lookup/resolve-entity c)]
      (if res
        (symbol
         (tawny.lookup/resolve-entity c))
        `(~'iri ~(tawny.lookup/named-entity-as-string c))))
    :object
    c))

(defmethod form OWLClass [c]
  (entity-or-iri c))

(defmethod form OWLProperty [p]
  (entity-or-iri p))

(defmethod form OWLIndividual [i]
  (entity-or-iri i))

(defmethod form OWLObjectOneOf
  [^OWLObjectOneOf o]
  (list*
   (exp oneof object-oneof)
   (form (.getIndividuals o))))

(defmethod form OWLObjectSomeValuesFrom
  [^OWLObjectSomeValuesFrom s]
  (list
   (exp owl-some object-some)
   (form (.getProperty s))
   (form (.getFiller s))))

(defmethod form OWLObjectUnionOf
  [^OWLObjectUnionOf u]
   (list*
    (exp owl-or object-or)
    (form (.getOperands u))))

(defmethod form OWLObjectIntersectionOf
  [^OWLObjectIntersectionOf c]
  (list*
   (exp owl-and object-or) (form (.getOperands c))))

(defmethod form OWLObjectAllValuesFrom
  [^OWLObjectAllValuesFrom a]
  (list
   (exp
    only
    object-only)
        (form (.getProperty a))
        (form (.getFiller a))))

(defmethod form OWLObjectComplementOf
  [^OWLObjectComplementOf c]
  (list
   (exp owl-not
        object-not)
        (form (.getOperand c))))

(defmethod form OWLObjectExactCardinality
  [^OWLObjectExactCardinality c]
  (list
   (exp exactly object-exactly)
   (.getCardinality c)
        (form (.getProperty c))
        (form (.getFiller c))))

(defmethod form OWLObjectMaxCardinality
  [^OWLObjectMaxCardinality c]
  (list
   (exp at-most object-at-most) (.getCardinality c)
        (form (.getProperty c))
        (form (.getFiller c))))

(defmethod form OWLObjectMinCardinality
  [^OWLObjectMinCardinality c]
  (list (exp at-least object-at-least)
        (.getCardinality c)
        (form (.getProperty c))
        (form (.getFiller c))))

(defmethod form OWLAnnotation
  [^OWLAnnotation a]
  (let [v (.getValue a)]
    (cond
     (.. a getProperty isLabel)
     (list 'label
           (form v))
     (.. a getProperty isComment)
     (list 'owl-comment
           (form v))
     :default
     (list
      'annotation
      (form (.getProperty a))
      (form v)))))

(defmethod form OWLAnnotationProperty [p]
  (entity-or-iri p))

;; this can be improved somewhat -- not converting classes into something
;; readable.
(defmethod form OWLAnnotationValue
  [^Object v]
  (list
   (str v)))

(def
  ^{:private true}
  owldatatypes-inverted
  (into {}
        (for [[k
               ^OWL2Datatype v] owl/owl2datatypes]
          [(.getDatatype v (owl/owl-data-factory)) k])))

(defmethod form OWLLiteral
  [^OWLLiteral l]
  (list*
   'literal
   (.getLiteral l)
   (if (.hasLang l)
     [:lang (.getLang l)]
     [:type
      (form (.getDatatype l))])))

;; so, in many cases, fillers can be an Datatype, which is probably going
;; to render as a keyword. Alternatively, it might be a DataRange which is
;; going to render as one or more span elements. The former needs to be
;; include directly, the latter needs not
(defn- list**
  "Operates like list if the list or list* depending on whether the last
element is a list."
  [& args]
  (if (seq? (last args))
    (apply list* args)
    (apply list args)))

(defmethod form OWLDataSomeValuesFrom
  [^OWLDataSomeValuesFrom d]
  (list**
   (exp owl-some data-some)
   (form (.getProperty d))
   (form (.getFiller d))))

(defmethod form OWLDataAllValuesFrom
  [^OWLDataAllValuesFrom a]
  (list
   (exp only data-only)
        (form (.getProperty a))
        (form (.getFiller a))))

(defmethod form OWLDataComplementOf
  [^OWLDataComplementOf c]
  (list
   (exp owl-not data-not)
        (form (.getDataRange c))))

(defmethod form OWLDataExactCardinality
  [^OWLDataExactCardinality c]
  (list
   (exp exactly data-exactly)
   (.getCardinality c)
        (form (.getProperty c))
        (form (.getFiller c))))

(defmethod form OWLDataMaxCardinality
  [^OWLDataMaxCardinality c]
  (list
   (exp at-most data-at-most)
   (.getCardinality c)
        (form (.getProperty c))
        (form (.getFiller c))))

(defmethod form OWLDataMinCardinality
  [^OWLDataMinCardinality c]
  (list
   (exp at-least data-at-least)
   (.getCardinality c)
        (form (.getProperty c))
        (form (.getFiller c))))

(defn- numeric-literal
  "Returns a number from one of the numerous typed wrappers."
  [^OWLLiteral l]
  (cond
   (.isInteger l)
   (.parseInteger l)
   (.isFloat l)
   (.parseFloat l)
   (.isDouble l)
   (.parseDouble l)
   :default
   (throw (IllegalArgumentException. "Non numeric literal passed to numeric-literal"))))

(defn- numeric-facet [d]
  (get
       {OWLFacet/MAX_EXCLUSIVE '<
        OWLFacet/MAX_INCLUSIVE '<=
        OWLFacet/MIN_EXCLUSIVE '>
        OWLFacet/MIN_INCLUSIVE '>=
        }
       d))

(defmethod form OWLDatatypeRestriction
  [^OWLDatatypeRestriction d]
  (let [dt (.getDatatype d)]
    (cond
     (or
      (.isDouble dt)
      (.isFloat dt)
      (.isInteger dt))
     (for [^OWLFacetRestriction fr (.getFacetRestrictions d)]
       (list 'span
             (numeric-facet (.getFacet fr))
             (numeric-literal (.getFacetValue fr))))
     :default
     (throw (Exception. "Can't render non-numeric datatype")))))

(defmethod form OWLFacetRestriction
  [^OWLFacetRestriction d]
  (list (form (.getFacet d)) (form (.getFacetValue d))))

(defmethod form org.semanticweb.owlapi.model.OWLDatatype [d]
  (if-let [x (get owldatatypes-inverted d)]
    ;; it's a builtin, so reverse lookup keyword
    x
    (entity-or-iri d)))

(defmethod form org.semanticweb.owlapi.model.OWLObjectHasValue
  [^OWLObjectHasValue p]
  (list (exp has-value object-has-value)
        (form (.getProperty p))
        (form (.getValue p))))

(defmethod form org.semanticweb.owlapi.model.OWLObjectHasSelf
  [^OWLObjectHasSelf s]
  (list 'has-self (form (.getProperty s))))

(defmethod form org.semanticweb.owlapi.model.OWLDataHasValue
  [^OWLDataHasValue p]
  (list (exp has-value data-has-value)
        (form (.getProperty p))
        (form (.getValue p))))

(defmethod form OWLObjectInverseOf
  [^OWLObjectInverseOf p]
  (list 'inverse (form (.getInverse p))))


(defmethod form String [e]
  e)

;; obviously this is error trap!
(defmethod form :default [e]
  (do
    (println "Unknown form" (class e))
    (Thread/dumpStack)
    `(unknown form)))
