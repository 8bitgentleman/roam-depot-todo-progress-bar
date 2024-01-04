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
        tasks (r/atom {:todo (count-occurrences "TODO" pages-referenced)
                       :doing (count-occurrences "DOING" pages-referenced)
                       :done (count-occurrences "DONE" pages-referenced)
                       :blocked (count-occurrences "BLOCKED" pages-referenced)
                       :canceled (count-occurrences "CANCELED" pages-referenced)})]
    (let [done-tasks (:done @tasks)
          todo-tasks (:todo @tasks)
          doing-tasks (:doing @tasks)
          blocked-tasks (:blocked @tasks)
          max-value (apply max (map @tasks [:done :todo :doing :blocked]))
          done-percentage (* 100 (/ done-tasks max-value))
          todo-percentage (* 100 (/ todo-tasks max-value))
          doing-percentage (* 100 (/ doing-tasks max-value))
          blocked-percentage (* 100 (/ blocked-tasks max-value))]
      [:div
       [:div {:style {:display "flex"
                      :align-items "center"}}
        [:div {:style {:width "140px" :height "6px" :background-color "#ddd" :display "flex" :border-radius "3px" :overflow "hidden" :margin-left "10px" :margin-right "10px"}}
         [:div.done {:style {:width (str (* done-percentage 1.4) "px") :height "6px" :background-color "#137CBD"}}]
         [:div.blocked {:style {:width (str (* blocked-percentage 1.4) "px") :height "6px" :background-color "#db3737"}}]
         [:div.doing {:style {:width (str (* doing-percentage 1.4) "px") :height "6px" :background-color "#009E23"}}]
         [:div.todo {:style {:width (str (* todo-percentage 1.4) "px") :height "6px" :background-color "#ddd" :border-radius "0px 3px 3px 0px"}}]
        ]
        [:span [:div  (str done-tasks  "/"
                           (+ done-tasks
                              todo-tasks
                              doing-tasks
                              blocked-tasks)
                           " Done"
                           )]]
       ]
      ])))