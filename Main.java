
import java.util.ArrayList;
import java.util.List;
import java.util.ArrayList;
import java.util.List;
public class Main{

    public static void main(String[] args) {
        String html = "";
        try {
            html = java.nio.file.Files.readString(java.nio.file.Path.of("index.html"));
        } catch (java.io.IOException e) {
            System.err.println("Failed to read index.html: " + e.getMessage());
        }
        List<Token> tokensWithSpaces = Tokenizer.tokenize(html);
        List<Token> tokens = Tokenizer.removeWhitespaceTokens(tokensWithSpaces);
        List<Variable> variables = analyzeVariables(tokens);
        List<Node> roots = parseHTML(tokens,0,tokens.size()-1);
        List<Node> rootsWithSpaces = parseHTML(tokensWithSpaces,0,tokensWithSpaces.size()-1);
        generateBindings(variables,roots);
        String compiledHTML = compileHTML(roots, variables,html,rootsWithSpaces);
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("output.html"), compiledHTML);
        } catch (java.io.IOException e) {
            System.err.println("Failed to write output.html: " + e.getMessage());
        }
        

        

    }

    static Node findScriptTag(List<Node> roots){
        for(Node root : roots){
            if(root.type().equals("script")){
                return root;
            }
            Node found = findScriptTag(root.children());
            if(found != null) return found;
        }
        return null;
    }

    static String compileHTML(List<Node> roots, List<Variable> variables, String originalHTML,List<Node> rootsWithSpaces){
        Node scriptTag  = findScriptTag(roots);
        String helperFunctions[] = new String[variables.size()];
        int index = 0;
        for(Variable var : variables){
            helperFunctions[index] = "function update_" + var.name + "(" + var.name +"){\n";
            for(Binding binding : var.bindings()){
                if(binding.node().id() != null){
                    helperFunctions[index] += "  document.getElementById(\"" + binding.node().id() + "\").innerHTML = "  + binding.transformExpression() + ";\n";
                }
            }
            helperFunctions[index] += "}\n";
            index++;
        }
        for(String func : helperFunctions){
            IO.println("Helper Function:\n" + func);
        }
        for(int i=0;i<scriptTag.tokens().size();i++){
            Token t = scriptTag.tokens().get(i);
            
            for(Variable var : variables){
                if(t.type() == TokenType.IDENTIFIER && t.value().equals(var.name)){
                    if(i+1 < scriptTag.tokens().size() && scriptTag.tokens().get(i+1).type() == TokenType.EQUALS){
                        List<Token> rhs = new ArrayList<>();
                        int j = i + 2;
                        while(j < scriptTag.tokens().size() && scriptTag.tokens().get(j).type() != TokenType.SEMICOLON){
                            if(scriptTag.tokens().get(j).type() == TokenType.STRING){
                                rhs.add(new Token(TokenType.STRING, "\"" + scriptTag.tokens().get(j).value() + "\""));
                            }
                            else rhs.add(scriptTag.tokens().get(j));
                            j++;
                        }
                        StringBuilder rhsBuilder = new StringBuilder();
                        for(Token rt : rhs){
                            rhsBuilder.append(rt.value());
                        }
                        String rhsCode = rhsBuilder.toString();
                        String tempVar = "temp_" + var.name; // Temporary variable
                        String tempAssign = tempVar + " = " + rhsCode + ";";
                        String updateCall = "update_" + var.name + "(" + tempVar + ");";
                        IO.println("Inserting update call: " + updateCall);
                        // Insert temp assignment BEFORE the variable assignment
                        scriptTag.tokens().add(i-2, new Token(TokenType.VARDEC, "let"));
                        scriptTag.tokens().add(i-1, new Token(TokenType.IDENTIFIER, tempAssign));
                        // Adjust j since we inserted before i
                        j = j + 1;
                        // Insert update call after the variable assignment
                        scriptTag.tokens().add(i, new Token(TokenType.IDENTIFIER, updateCall));
                        i = j; // Move i to after the inserted calls
                    }
                    else if(i+1 < scriptTag.tokens().size() && scriptTag.tokens().get(i+1).type() == TokenType.PLUSPLUS){
                        String updateCall = "update_" + var.name + "(" + var.name + ");";
                        IO.println("Inserting update call for ++: " + updateCall);
                        // Insert updateCall after the ++
                        scriptTag.tokens().add(i + 3, new Token(TokenType.IDENTIFIER, updateCall));
                        i = i + 2; // Move i to after the inserted call
                    }
                }
            }
            
        }
        List<List<Node>> bindingNodes = getBindingNodes(variables, rootsWithSpaces); 
        IO.println("Binding Nodes in HTML with spaces:");
        for(List<Node> nodeList : bindingNodes){
            for(Node n : nodeList){
                IO.println("  Node Type: " + n.type());
            }
        }
        scriptTag.tokens().add(indexOf(scriptTag.tokens(),TokenType.CBRACKET)+1, new Token(TokenType.IDENTIFIER, String.join("\n", helperFunctions)));
        patchWhitespaces(scriptTag);
        Tokenizer.printTokens(scriptTag.tokens());
        replaceNode(rootsWithSpaces, scriptTag);
        return reconstructHTML(rootsWithSpaces);
    }

    static int indexOf(List<Token> tokens, TokenType type){
        for(int i = 0;i<tokens.size();i++){
            if(tokens.get(i).type() == type){
                return i;
            }
        }
        return -1;
    }

    static void patchWhitespaces(Node scriptTag){
    
        for(int i = 0;i<scriptTag.tokens().size();i++){
            if(scriptTag.tokens().get(i).type() == TokenType.VARDEC || scriptTag.tokens().get(i).type() == TokenType.IDENTIFIER){
                scriptTag.tokens().add(i+1, new Token(TokenType.WHITESPACE," "));
                i++;
            }
        }  
        
    }

    static List<List<Node>> getBindingNodes(List<Variable> variables, List<Node> roots){
        List<List<Node>> bindingNodes = new ArrayList<>();
        for(Variable var : variables){
            List<Node> nodes = new ArrayList<>();
            for(Node root : roots){
                collectBindingNodes(var, root, nodes);
            }
            bindingNodes.add(nodes);
        }
        return bindingNodes;
    }

    static void collectBindingNodes(Variable var, Node current, List<Node> nodes){
        if(var.bindingNodes().contains(current)){
            nodes.add(current);
        }
        for(Node child : current.children()){
            collectBindingNodes(var, child, nodes);
        }
    }

    static void replaceNode(List<Node> roots, Node target){
        for(int i = 0;i<roots.size();i++){
            if(roots.get(i).type() != null && target.type() != null && roots.get(i).type().equals(target.type())){
                roots.set(i, target);
                return;
            }
            replaceNode(roots.get(i).children(), target);
        }
    }

    static String reconstructHTML(List<Node> roots){
        StringBuilder htmlBuilder = new StringBuilder();
        for(Node root : roots){
            List<Token> tokens = root.tokens();
            int childIndex = 0;
            for(int i = 0; i < tokens.size(); i++){
                Token t = tokens.get(i);
                if(null == t.type()) htmlBuilder.append(t.value());
                else switch (t.type()) {
                    case NEWLINE -> htmlBuilder.append("\n");
                    case STRING -> htmlBuilder.append("\"").append(t.value()).append("\"");
                    default -> htmlBuilder.append(t.value());
                }
                // After opening tag, insert children before closing tag
                if(t.type() == TokenType.CBRACKET && i > 0 && tokens.get(i-1).type() != TokenType.SLASH){
                    // Check if next token starts a closing tag
                    boolean isClosingTagNext = (i+1 < tokens.size() && tokens.get(i+1).type() == TokenType.OBRACKET && 
                                              i+2 < tokens.size() && tokens.get(i+2).type() == TokenType.SLASH);
                    if(isClosingTagNext || childIndex < root.children().size()){
                        htmlBuilder.append(reconstructHTML(root.children()));
                        childIndex = root.children().size();
                    }
                }
            }
        }
        return htmlBuilder.toString();
    }

    
    static void generateBindings(List<Variable> variables, List<Node> roots){
       for(Node root : roots){
           for(Variable var : variables){
               for(int i = 0;i<root.tokens().size();i++){
                   if(root.tokens().get(i).type() == TokenType.OBRACE){
                       List<Token> bindingTokens = new ArrayList<>();
                       i++;
                       while(i < root.tokens().size() && root.tokens().get(i).type() != TokenType.CBRACE){
                           bindingTokens.add(root.tokens().get(i));
                           i++;
                       }
                       StringBuilder bindingExprBuilder = new StringBuilder();
                       for(Token bt : bindingTokens){
                            if(bt.type() == TokenType.STRING){
                                bindingExprBuilder.append("\"").append(bt.value()).append("\"");
                            }
                            else bindingExprBuilder.append(bt.value());
                        }
                        String bindingExpr = bindingExprBuilder.toString();
                        if(bindingExpr.contains(var.name)) var.bindings().add(new Binding(bindingExpr, root));
                        
                   }
               }
           }
           generateBindings(variables,root.children());
       }
    }


    static int findMatchingClosingTag(List<Token> tokens, int start, int end, String tagName){
        int depth = 0;
        for(int i = start;i<=end;i++){
            if(tokens.get(i).type() == TokenType.OBRACKET){
                if(i+1 <= end && tokens.get(i+1).type() == TokenType.SLASH){
                    if(i+2 <= end && tokens.get(i+2).type() == TokenType.IDENTIFIER && tokens.get(i+2).value().equals(tagName)){
                        if(depth == 0){
                            return i;
                        }else{
                            depth--;
                        }
                    }
                }else if(i+1 <= end && tokens.get(i+1).type() == TokenType.IDENTIFIER && tokens.get(i+1).value().equals(tagName)){
                    depth++;
                }
            }
        }
        return -1;
    }


    static List<Node> parseHTML(List<Token> tokens, int start, int end){
        List<Node> nodes = new ArrayList<>();
        int i = start;
        while(i <= end){
            Token t = tokens.get(i);
            // Found an opening tag
            if(t.type() == TokenType.OBRACKET && i+1 <= end && tokens.get(i+1).type() == TokenType.IDENTIFIER){
                int openStart = i;
                int openEnd = i;
                // find end of opening tag (CBRACKET)
                while(openEnd <= end && tokens.get(openEnd).type() != TokenType.CBRACKET) openEnd++;
                if(openEnd > end) break; // malformed
                String tagName = tokens.get(i+1).value();
                // parse id attribute inside opening tag if present
                String id = null;
                for(int a = openStart; a <= openEnd; a++){
                    if(tokens.get(a).type() == TokenType.IDENTIFIER && tokens.get(a).value().equals("id")){
                        if(a+1 <= openEnd && tokens.get(a+1).type() == TokenType.EQUALS && a+2 <= openEnd && tokens.get(a+2).type() == TokenType.STRING){
                            String raw = tokens.get(a+2).value();
                            if(raw.length() >= 2){
                                char c0 = raw.charAt(0);
                                char c1 = raw.charAt(raw.length()-1);
                                if((c0 == '"' && c1 == '"') || (c0 == '\'' && c1 == '\'')){
                                    raw = raw.substring(1, raw.length()-1);
                                }
                            }
                            id = raw;
                            break;
                        }
                    }
                }
                // find matching closing tag (search after the opening tag's CBRACKET)
                int closingIndex = findMatchingClosingTag(tokens, openEnd+1, end, tagName);
                int contentStart = openEnd + 1;
                int contentEnd = (closingIndex == -1) ? end : closingIndex - 1;
                // parse children from the content region
                List<Node> children = new ArrayList<>();
                if(contentStart <= contentEnd){
                    children = parseHTML(tokens, contentStart, contentEnd);
                }
                // build the token list for this node: include opening tag, any content tokens that are NOT part of children, and the closing tag
                List<Token> nodeTokens = new ArrayList<>();
                // opening tag tokens
                for(int k = openStart; k <= openEnd; k++) nodeTokens.add(tokens.get(k));
                // iterate content and skip child ranges
                int k = contentStart;
                while(k <= contentEnd){
                    if(tokens.get(k).type() == TokenType.OBRACKET && k+1 <= contentEnd && tokens.get(k+1).type() == TokenType.IDENTIFIER){
                        // find end of this child's opening tag
                        int childOpenEnd = k;
                        while(childOpenEnd <= contentEnd && tokens.get(childOpenEnd).type() != TokenType.CBRACKET) childOpenEnd++;
                        if(childOpenEnd > contentEnd){
                            // malformed, just include the token and move on
                            nodeTokens.add(tokens.get(k));
                            k++;
                            continue;
                        }
                        String childTag = tokens.get(k+1).value();
                        // find matching closing for the child (search after child's opening CBRACKET)
                        int childClosing = findMatchingClosingTag(tokens, childOpenEnd+1, contentEnd, childTag);
                        if(childClosing == -1){
                            // malformed child, include the opening bracket token and continue
                            nodeTokens.add(tokens.get(k));
                            k++;
                        } else {
                            // find end index of the child's closing tag (the CBRACKET of the closing tag)
                            int childCloseEnd = childClosing;
                            while(childCloseEnd <= contentEnd && tokens.get(childCloseEnd).type() != TokenType.CBRACKET) childCloseEnd++;
                            // skip entire child (from k to childCloseEnd)
                            k = childCloseEnd + 1;
                        }
                    } else {
                        nodeTokens.add(tokens.get(k));
                        k++;
                    }
                }
                // add closing tag tokens if present
                if(closingIndex != -1){
                    int closeEnd = closingIndex;
                    while(closeEnd <= end && tokens.get(closeEnd).type() != TokenType.CBRACKET) closeEnd++;
                    for(int p = closingIndex; p <= closeEnd; p++) nodeTokens.add(tokens.get(p));
                    i = closeEnd + 1;
                } else {
                    // no closing tag found, we're done with this segment
                    i = end + 1;
                }
                nodes.add(new Node(tagName, nodeTokens, children, id));
                continue;
            }
            i++;    
        }
        return nodes;
    }
     
    private static List<Variable> analyzeVariables(List<Token> tokens){
        List<Variable> variables = new ArrayList<>();
        for(int i = 0;i<tokens.size();i++){
            if(tokens.get(i).type() == TokenType.OBRACKET){
                if(i+1 < tokens.size() && tokens.get(i+1).type() == TokenType.IDENTIFIER && tokens.get(i+1).value().equals("script")){
                    // move i to after the opening tag's CBRACKET
                    int openEnd = i;
                    while(openEnd < tokens.size() && tokens.get(openEnd).type() != TokenType.CBRACKET) openEnd++;
                    i = openEnd + 1;
                    // parse variable declarations until next tag
                    while(i < tokens.size() && tokens.get(i).type() != TokenType.OBRACKET){
                        if(tokens.get(i).type() == TokenType.IDENTIFIER && tokens.get(i-1).type() == TokenType.VARDEC){
                            String varName = tokens.get(i).value();
                            Variable var = new Variable(varName,new ArrayList<>());
                            // try to parse an optional "= value" after the identifier
                            int j = i + 1;
                            if(j < tokens.size() && tokens.get(j).type() == TokenType.EQUALS){
                                j++;
                                if(j < tokens.size()){
                                    String raw = tokens.get(j).value();
                                    // strip quotes for string literals
                                    if(tokens.get(j).type() == TokenType.STRING && raw.length() >= 2){
                                        char c0 = raw.charAt(0);
                                        char c1 = raw.charAt(raw.length()-1);
                                        if((c0 == '"' && c1 == '"') || (c0 == '\'' && c1 == '\'')){
                                            raw = raw.substring(1, raw.length()-1);
                                        }
                                    }
                                    var.value = raw;
                                    // advance j past the value; if there's a semicolon consume it
                                    j++;
                                    if(j < tokens.size() && tokens.get(j).type() == TokenType.SEMICOLON) j++;
                                    i = j;
                                } else {
                                    i = j;
                                }
                            } else {
                                // no value, move past the identifier
                                i++;
                            }
                            variables.add(var);
                            continue;
                        }
                        i++;
                    }
                }
            }    
        }
        return variables;
    }


   
    

}


record Node(String type,List<Token> tokens, List<Node> children,String id){
    void print(String tab){
        IO.print(tab + "Node Type: " + type + ", Value: ");
        Tokenizer.printTokens(tokens);
        IO.println("");
        for(Node child : children){
            child.print(tab + "  ");
        }
    }
}

record Binding(String transformExpression,Node node){
    
}
class Tokenizer {
    
    public static void printTokens(List<Token> tokens){
        for(Token token : tokens){
            IO.println(token.type() + " : " + token.value());
        }
    }

    public static List<Token> removeWhitespaceTokens(List<Token> tokens){
        List<Token> filtered = new ArrayList<>();
        for(Token token : tokens){
            if(token.type() != TokenType.WHITESPACE){
                filtered.add(token);
            }
        }
        return filtered;
    }

    public static List<Token> tokenize(String html){
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while(i < html.length()){
            char c = html.charAt(i);

            if(c == '+' && i + 1 < html.length() && html.charAt(i + 1) == '+'){
                tokens.add(new Token(TokenType.PLUSPLUS, "++"));
                i += 2;
            } else if(c == '-' && i + 1 < html.length() && html.charAt(i + 1) == '>'){
                tokens.add(new Token(TokenType.ARROW, "->"));
                i += 2;
            } else if(c == '<'){
                tokens.add(new Token(TokenType.OBRACKET,"<"));
                i++;
            } else if(c == '>'){
                tokens.add(new Token(TokenType.CBRACKET,">"));
                i++;
            } else if(c == ';'){
                tokens.add(new Token(TokenType.SEMICOLON,";"));
                i++;
            } else if(c == '{'){
                tokens.add(new Token(TokenType.OBRACE,"{"));
                i++;
            } else if(c == '}'){
                tokens.add(new Token(TokenType.CBRACE,"}"));
                i++;
            } else if(c == '('){
                tokens.add(new Token(TokenType.OPAREN,"("));
                i++;
            } else if(c == ')'){
                tokens.add(new Token(TokenType.CPAREN,")"));
                i++;
            } else if(c == '/'){
                tokens.add(new Token(TokenType.SLASH,"/"));
                i++;
            } else if(c == '='){
                tokens.add(new Token(TokenType.EQUALS,"="));    
                i++;
            } else if(c == '!'){
                tokens.add(new Token(TokenType.EXCLAMATION,"!"));
                i++;
            } else if(c == '\n' || c == '\r'){
                tokens.add(new Token(TokenType.NEWLINE,"\\n"));
                i++;
            } else if(c == ' ' || c == '\t'){
                StringBuilder wsBuilder = new StringBuilder();
                while(i < html.length() && (html.charAt(i) == ' ' || html.charAt(i) == '\t')){
                    wsBuilder.append(html.charAt(i));
                    i++;
                }
                tokens.add(new Token(TokenType.WHITESPACE, wsBuilder.toString()));
            } else if(Character.isDigit(c)){
                StringBuilder numBuilder = new StringBuilder();
                while(i < html.length() && Character.isDigit(html.charAt(i))){
                    numBuilder.append(html.charAt(i));
                    i++;
                }
                tokens.add(new Token(TokenType.NUMBER, numBuilder.toString()));
            } else if(c == '"' || c == '\''){
                char quoteType = c;
                StringBuilder strBuilder = new StringBuilder();
                i++; // skip opening quote
                while(i < html.length() && html.charAt(i) != quoteType){
                    strBuilder.append(html.charAt(i));
                    i++;
                }
                // skip closing quote if present
                if(i < html.length() && html.charAt(i) == quoteType) i++;
                tokens.add(new Token(TokenType.STRING, strBuilder.toString()));
            } else if(c == '.'){
                tokens.add(new Token(TokenType.DOT, "."));
                i++;
            } else if(c == ','){
                tokens.add(new Token(TokenType.COMMA, ","));
                i++;
            } else if(c == '*'){
                tokens.add(new Token(TokenType.MULTIPLY, "*"));
                i++;
            } else if(c == '+'){
                tokens.add(new Token(TokenType.PLUS, "+"));
                i++;
            } else if(Character.isLetter(c)){
                StringBuilder idBuilder = new StringBuilder();
                while(i < html.length() && (Character.isLetterOrDigit(html.charAt(i)) || html.charAt(i) == '-' || html.charAt(i) == '_')){
                    idBuilder.append(html.charAt(i));
                    i++;
                } 
                String id = idBuilder.toString();
                if(id.equals("let") || id.equals("const") || id.equals("var"))
                    tokens.add(new Token(TokenType.VARDEC, id));
                else
                    tokens.add(new Token(TokenType.IDENTIFIER, id));
            } else {
                // if nothing matched, just advance to avoid infinite loop
                i++;
            }

        }
        return tokens;
            
    }
}

record Token(TokenType type, String value){}


enum TokenType{
    OBRACKET,
    CBRACKET,
    OBRACE,
    CBRACE,
    IDENTIFIER,
    SLASH,
    EQUALS,
    EXCLAMATION,
    STRING,
    SEMICOLON,
    NUMBER,
    VARDEC,
    NEWLINE,
    PLUSPLUS,
    ARROW,
    OPAREN,
    CPAREN,
    WHITESPACE,
    DOT,
    COMMA,
    MULTIPLY,
    PLUS
}


class Variable{
    public String name;
    public String value;
    List<Binding> bindings;
    public Variable(String name,List<Binding> bindings){
        this.name = name;
        this.bindings = bindings;
    }

    public List<Binding> bindings(){
        return bindings;
    }

    public List<Node> bindingNodes(){
        List<Node> nodes = new ArrayList<>();
        for(Binding b : bindings){
            nodes.add(b.node());
        }
        return nodes;
    }
   

}


