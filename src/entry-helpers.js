const codeBlockUID = 'roam-render-todo-progress-cljs';
const cssBlockUID = 'roam-render-todo-progress-css';

function removeCodeBlock(uid){
    roamAlphaAPI.deleteBlock({"block":{"uid": uid}})
}

function uidForToday() {
    let roamDate = new Date(Date.now());
    let today = window.roamAlphaAPI.util.dateToPageTitle(roamDate);
    return today
}

function createPage(title){
    // creates the roam/css page if it does not exist
    let pageUID = roamAlphaAPI.util.generateUID()
    roamAlphaAPI.data
        .page.create(
            {"page": 
                {"title": title, 
                "uid": pageUID}})
    return pageUID;
}

function getPageUidByPageTitle(title){
    return roamAlphaAPI.q(
        `[:find (pull ?e [:block/uid]) :where [?e :node/title "${title}"]]`
        )?.[0]?.[0].uid || null
}

function createRenderBlock(renderPageName, titleblockUID){
    let renderPageUID = getPageUidByPageTitle(renderPageName)|| createPage(renderPageName);
    let templateBlockUID = roamAlphaAPI.util.generateUID()
    let codeBlockHeaderUID = roamAlphaAPI.util.generateUID()
    let renderBlockUID = roamAlphaAPI.util.generateUID()

    // create the titleblock
    //TODO Progress Bar [[January 12th, 2023]]
    roamAlphaAPI
    .createBlock(
        {"location": 
            {"parent-uid": renderPageUID, 
            "order": 0}, 
        "block": 
            {"string": `TODO Progress Bar [[${uidForToday()}]]`,
            "uid":titleblockUID,
            "open":true,
            "heading":3}})
    // create the template name block
    // TODO Progress Bar v10 [[roam/templates]]
    roamAlphaAPI
    .createBlock(
        {"location": 
            {"parent-uid": titleblockUID, 
            "order": 0}, 
        "block": 
            {"string": `TODO Progress Bar v10 [[roam/templates]]`,
            "uid":templateBlockUID,
            "open":true}})
    // create the render component block
    // {{roam/render:((diA0Fyj5m))}}
    roamAlphaAPI
    .createBlock(
        {"location": 
            {"parent-uid": templateBlockUID, 
            "order": 0}, 
        "block": 
            {"string": `{{[[roam/render]]:((${codeBlockUID}))}}`,
            "uid":renderBlockUID}})

    // create code header block
    roamAlphaAPI
    .createBlock(
        {"location": 
            {"parent-uid": titleblockUID, 
            "order": 'last'}, 
        "block": 
            {"string": `code`,
            "uid":codeBlockHeaderUID,
            "open":false}})

            // create codeblock for the component

    let cljs = `
(ns progress-bar-v10
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

(defn count-occurrences 
"Counts the occurances of a string in a list"
[s slist]
(->> slist
        flatten
        (filter #{s})
        count))


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
(let [tasks (r/atom {;; don't love that I do this search twice
                        :todo (count-occurrences "TODO" (recurse-search block-uid))
                        :done (count-occurrences "DONE" (recurse-search block-uid))} )]
    
    [:div
            [:div {:style {:display "flex"
                                :align-items "center"}
                        }
                    [:span [:progress {
                    :id "file"
                    :name "percent-done"
                    :value (:done @tasks)
                    :max (+ (:todo @tasks) (:done @tasks))
                    :style{

                            :margin-left "10px"
                            :margin-right "10px"
                            }}]
                    ]
                    [:span [:div  (str (:done @tasks)  "/"
                                    (+ 
                                        (:done @tasks)
                                        (:todo @tasks))
                                    " Done"
                                    )]]

                    ]
            ]
)); 
                `;
                
    let blockString = "```clojure\n " + cljs + " ```"
    roamAlphaAPI
    .createBlock(
        {"location": 
            {"parent-uid": codeBlockHeaderUID, 
            "order": 0}, 
        "block": 
            {"uid": codeBlockUID,
            "string": blockString}})
    
}


function createCSSBlock(parentUID){
    // creates the initial code block and its parent
    // adding this to the roam/css page so users can use it as an example
    // if roam/css page doesn't exist then create it
    let pageUID = getPageUidByPageTitle('roam/css') || createPage('roam/css');
    // create closed parent block
    roamAlphaAPI
    .createBlock(
        {"location": 
            {"parent-uid": pageUID, 
            "order": "last"}, 
        "block": 
            {"string": `TODO PROGRESS BAR STYLE [[${uidForToday()}]]`,
            "uid":parentUID,
            "open":false,
            "heading":3}})
    
    // create codeblock for a todo progress bar
    // I do this so that a user can see what to customize
    let css = `
/* THIS CODEBLOCK IS OVERWRITTEN ON EVERY VERSION UPDATE
DO NOT MODIFY*/
:root{
    --progress-bar-default:#137cbd;
    --progress-bar:#137cbd;
    --progress-border:#B6B6B6;
    --progress-bg:#dfe2e5;
}
:root .rm-dark-theme {
    --progress-border:#137cbd;
    --progress-bg:#EFEFEF;
}
    
progress[name="percent-done"],
.todo-progress-bar progress{
    display:inline-block;
    height:6px;
    background:none;
    border-radius: 15px;
    margin-bottom:2px;
}
    
progress::-webkit-progress-bar,
.todo-progress-bar progress::-webkit-progress-bar{
    height:6px;
    background-color: var(--progress-bg);
    border-radius: 15px;
}

.rm-dark-theme progress::-webkit-progress-bar,
.rm-dark-theme .todo-progress-bar progress::-webkit-progress-bar{
  box-shadow:0px 0px 6px var(--progress-border) inset;
}

progress::-webkit-progress-value,
.todo-progress-bar progress::-webkit-progress-value{
    display:inline-block;
    float:left;
    height:6px;
    margin:0px -10px 0 0;
    background: var(--progress-bar);
    border-radius: 5px;
}
    `;

    let blockString = "```css\n " + css + " ```"
    roamAlphaAPI
    .createBlock(
        {"location": 
            {"parent-uid": parentUID, 
            "order": 0}, 
        "block": 
            {"uid": cssBlockUID,
            "string": blockString}})

}

function replaceRenderString(){
    // replaces the {{[[roam/render]]:((5juEDRY_n))}} string across the entire graph
    // I do this because when the original block is deleted Roam leaves massive codeblocks wherever it was ref'd
    // also allows me to re-add back if a user uninstalls and then re-installs
    let query = `[:find
        (pull ?node [:block/string :node/title :block/uid])
      :where
        (or [?node :block/string ?node-String]
      [?node :node/title ?node-String])
        [(clojure.string/includes? ?node-String "{{[[roam/render]]:((roam-render-todo-progress-cljs))}}")]
      ]`;
    let renderString = `{{[[roam/render]]:((${codeBlockUID}))}}`
    let result = window.roamAlphaAPI.q(query).flat();
    result.forEach(block => {
        const updatedString = block.string.replace(renderString, '{{todo-progress-bar}}');
        window.roamAlphaAPI.updateBlock({
          block: {
            uid: block.uid,
            string: updatedString
          }
        });
    });
}

export default function toggleProgressBar(state) {
    let titleblockUID = 'roam-render-todo-progress';
    let renderPageName = 'roam/render'
    // css
    let cssBlockParentUID = 'todo-progress-css-parent';

    if (state==true) {
        createRenderBlock(renderPageName, titleblockUID)
        createCSSBlock(cssBlockParentUID);
    } else if(state==false){
        replaceRenderString()
        removeCodeBlock(titleblockUID)
        removeCodeBlock(cssBlockParentUID)
    }
}
