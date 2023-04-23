import cssFile from "./todo-progress-bar.css";
import clsjFile from "./todo-progress-bar.cljs";
const codeBlockUID = 'roam-render-todo-progress-cljs';
const cssBlockUID = 'roam-render-todo-progress-css';
const renderString = `{{[[roam/render]]:((${codeBlockUID}))}}`
const replacementString = '{{todo-progress-bar}}'
const version = 'v11'

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
            {"string": `TODO Progress Bar ${version} [[roam/templates]]`,
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

    let cljs = clsjFile
                
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
    let css = cssFile.toString();

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

function replaceRenderString(renderString, replacementString){
    // replaces the {{[[roam/render]]:((5juEDRY_n))}} string across the entire graph
    // I do this because when the original block is deleted Roam leaves massive codeblocks wherever it was ref'd
    // also allows me to re-add back if a user uninstalls and then re-installs
    

    let query = `[:find
        (pull ?node [:block/string :node/title :block/uid])
      :where
        (or [?node :block/string ?node-String]
      [?node :node/title ?node-String])
        [(clojure.string/includes? ?node-String "${renderString}")]
      ]`;
    
    let result = window.roamAlphaAPI.q(query).flat();
    result.forEach(block => {
        const updatedString = block.string.replace(renderString, replacementString);
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
        // if there was a previous install re-add in the correct uid/renderString
        replaceRenderString(replacementString, renderString)
    } else if(state==false){
        
        replaceRenderString(renderString, replacementString)
        removeCodeBlock(titleblockUID)
        removeCodeBlock(cssBlockParentUID)
    }
}
