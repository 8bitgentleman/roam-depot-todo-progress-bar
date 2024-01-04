(ns progress-bar-v11
  (:require
   [reagent.core :as r]
   [datascript.core :as d]
   [roam.datascript.reactive :as dr]
   [clojure.pprint :as pp]))

; THIS CODEBLOCK IS OVERWRITTEN ON EVERY VERSION UPDATE
; DO NOT MODIFY

(defn flatten-block 
  "Flattens a blocks children into a flat list"
  [acc block]
  (reduce flatten-block
          (conj acc (dissoc block :block/children))
          (:block/children block)))

(defn find-child-refs
  "Returns all _refs for children blocks given a parent block uid"
  [block-uid]
  (flatten-block []
             @(dr/q '[:find (pull ?e [:block/refs{:block/children ...}]) .
                      :in $ ?uid
                      :where
                      [?e :block/uid ?uid]]
                    block-uid)))

(defn id-title 
  "Gets a page's title from its db id"
  [id]
  (:node/title @(dr/pull '[:node/title] id))
  )

(defn info-from-id [id]
  	(or (:node/title @(dr/pull '[:node/title] id))
      (map
       id-title 
       (map 
       		:db/id
       		(:block/refs @(dr/pull '[:block/refs] id))
       ))
      )
  )

(defn count-occurrences [task tasks]
   "Counts the occurances of a string in a list"
  (count (filter #(= task %) tasks)))


(defn recurse-search
  "Recursivly search through a block's children for all pages referenced"
  [block-uid]
  (->> block-uid
       (find-child-refs)
       (map :block/refs)
       (flatten)
       (map :db/id)
       (map  info-from-id)
       (flatten)))


(defn main [{:keys [block-uid]} & args]
  (let [pages-referenced (recurse-search block-uid)
      todo-count (count-occurrences "TODO" pages-referenced)
      doing-count (count-occurrences "DOING" pages-referenced)
      done-count (count-occurrences "DONE" pages-referenced)
      blocked-count (count-occurrences "BLOCKED" pages-referenced)
      canceled-count (count-occurrences "CANCELED" pages-referenced)
      tasks (r/atom {
          :todo todo-count
          :done done-count
          :doing doing-count
          :blocked blocked-count
          :canceled canceled-count})]
      (println tasks)
      ;(println (count-occurrences "TODO" (recurse-search block-uid)))
      [:div
             [:div {:style {:display "flex"
                                 :align-items "center"}
                         }
                   [:span [:progress {
                      :id "file"
                      :name "percent-done"
                      :value (:done @tasks)
                      :max (+ (:todo @tasks) (:done @tasks) (:blocked @tasks) (:doing @tasks))
                      :style{

                             :margin-left "10px"
                             :margin-right "10px"
                             }}]
                    ]
              
                    [:span [:div  (str (:done @tasks)  "/"
                                      (+ 
                                        (:done @tasks)
                                        (:todo @tasks)
                                        (:doing @tasks)
                                        (:blocked @tasks)
                                        )
                                    " Done"
                                    )]]

                   ]
           ]
  )) 