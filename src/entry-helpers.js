import componentCSSFile from "./component.css";
import clsjFile from "./component.cljs";
import strikethroughCSSFile from "./strikethrough.css";

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

async function createRenderBlock(renderPageName, titleblockUID, version, codeBlockUID, componentName) {
    console.log('Creating blocks with UIDs:', {
        titleblockUID,
        codeBlockUID,
        renderPageName
    });

    const renderPageUID = getPageUidByPageTitle(renderPageName) || await createPage(renderPageName);
    console.log('Render page UID:', renderPageUID);

    const templateBlockUID = roamAlphaAPI.util.generateUID();
    const codeBlockHeaderUID = roamAlphaAPI.util.generateUID();
    const renderBlockUID = roamAlphaAPI.util.generateUID();

    // Create blocks sequentially with await
    try {
        await new Promise(resolve => setTimeout(resolve, 500)); // Add delay before first block
        await window.roamAlphaAPI.createBlock({
            location: {
                "parent-uid": renderPageUID,
                order: 0
            },
            block: {
                string: `${componentName} [[${uidForToday()}]]`,
                uid: titleblockUID,
                open: true,
                heading: 3
            }
        });

        // Wait for title block to exist before creating children
        await window.roamAlphaAPI.createBlock({
            location: {
                "parent-uid": titleblockUID,
                order: 0
            },
            block: {
                string: `${componentName} ${version} [[roam/templates]]`,
                uid: templateBlockUID,
                open: true
            }
        });

        await window.roamAlphaAPI.createBlock({
            location: {
                "parent-uid": templateBlockUID,
                order: 0
            },
            block: {
                string: `{{[[roam/render]]:((${codeBlockUID}))}}`,
                uid: renderBlockUID
            }
        });

        await window.roamAlphaAPI.createBlock({
            location: {
                "parent-uid": titleblockUID,
                order: 'last'
            },
            block: {
                string: 'code',
                uid: codeBlockHeaderUID,
                open: false
            }
        });

        const cljs = clsjFile;
        const blockString = "```clojure\n " + cljs + " ```";
        
        await window.roamAlphaAPI.createBlock({
            location: {
                "parent-uid": codeBlockHeaderUID,
                order: 0
            },
            block: {
                uid: codeBlockUID,
                string: blockString
            }
        });

    } catch (error) {
        console.error('Error creating blocks:', error);
        throw error;
    }
}


async function createCSSBlock(parentUID, cssBlockUID, cssFile, parentString) {
    const pageUID = getPageUidByPageTitle('roam/css') || createPage('roam/css');
    
    try {
        // Create parent block
        await roamAlphaAPI.createBlock({
            location: {
                "parent-uid": pageUID,
                order: "last"
            },
            block: {
                string: `${parentString} [[${uidForToday()}]]`,
                uid: parentUID,
                open: false,
                heading: 3
            }
        });

        // Create CSS codeblock
        const css = cssFile.toString();
        const blockString = "```css\n " + css + " ```";
        
        await roamAlphaAPI.createBlock({
            location: {
                "parent-uid": parentUID,
                order: 0
            },
            block: {
                uid: cssBlockUID,
                string: blockString
            }
        });
    } catch (error) {
        console.error('Error creating CSS blocks:', error);
        throw error;
    }
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

export async function toggleStrikethroughCSS(state) {
    const codeBlockParentUID = 'strikethrough-css-parent';
    const codeBlockUID = 'strikethrough-css';
    if (state === true) {
        await createCSSBlock(codeBlockParentUID, codeBlockUID, strikethroughCSSFile, 'DONE Task Strikethrough STYLE');
    } else if (state === false) {
        removeCodeBlock(codeBlockParentUID);
    }
}

export async function updateCodeBlock(codeBlockUID) {
    try {
        const cljs = clsjFile;
        const blockString = "```clojure\n " + cljs + " ```";
        
        // Update the existing block with new content
        await roamAlphaAPI.updateBlock({
            block: {
                uid: codeBlockUID,
                string: blockString
            }
        });
        console.log("Successfully updated code block content");
        return true;
    } catch (error) {
        console.error("Error updating code block:", error);
        return false;
    }
}

export async function updateCSSBlock(cssBlockUID) {
    try {
        const css = componentCSSFile.toString();
        const blockString = "```css\n " + css + " ```";
        
        // Update the existing block with new content
        await roamAlphaAPI.updateBlock({
            block: {
                uid: cssBlockUID,
                string: blockString
            }
        });
        console.log("Successfully updated CSS block content");
        return true;
    } catch (error) {
        console.error("Error updating CSS block:", error);
        return false;
    }
}

export async function toggleRenderComponent(state, titleblockUID, cssBlockParentUID, version, renderString, replacementString, cssBlockUID, codeBlockUID, componentName) {
    const renderPageName = 'roam/render';
    if (state === true) {
        await createRenderBlock(renderPageName, titleblockUID, version, codeBlockUID, componentName);
        await createCSSBlock(cssBlockParentUID, cssBlockUID, componentCSSFile, `${componentName} STYLE`);
    } else if (state === false) {
        // TODO: since we're not doing anything on state=false maybe call this fn onLoadHelper?
        // replaceRenderString(renderString, replacementString);
        // removeCodeBlock(titleblockUID);
        // removeCodeBlock(cssBlockParentUID);
    }
}
