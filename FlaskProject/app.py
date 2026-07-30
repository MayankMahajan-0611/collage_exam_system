from flask import Flask, request, jsonify
import ollama
import json
import re
import traceback

app = Flask(__name__)


@app.route('/')
def home():
    return jsonify({"status": "AI Microservice is running!"})


@app.route('/generate_mcq', methods=['POST'])
def generate():
    data = request.json
    text = data.get('text', '')
    num_questions = int(data.get('num_questions', 3))

    # 1. SANITIZATION: Stitch broken PDF lines & remove formatting gaps
    clean_text = re.sub(r'\s+', ' ', text.replace('\n', ' ')).strip()

    if len(clean_text) < 100:
        return jsonify({"error": "Please provide more text content."}), 400

    try:
        print(f"⚙️ Asking Llama 3 to generate {num_questions} questions...")

        # 2. PROMPT: Force strictly valid JSON
        prompt = f"""
        Generate {num_questions} multiple-choice questions based on the text.
        Output ONLY a valid JSON array.
        Structure: [{{ "question": "...", "options": ["A", "B", "C", "D"], "answer": "..." }}]
        Text: {clean_text}
        """

        # 3. AI CALL
        response = ollama.chat(
            model='llama3',
            messages=[{'role': 'user', 'content': prompt}],
            format='json'
        )

        output_text = response['message']['content'].strip()

        # 4. JSON CLEANUP: Strip Markdown tags (e.g., ```json) that break loading
        clean_json = re.sub(r'^```json\s*|\s*```$', '', output_text, flags=re.MULTILINE)

        try:
            mcqs = json.loads(clean_json)
        except json.JSONDecodeError:
            # Fallback if AI returned an object instead of an array
            data_dict = json.loads(clean_json)
            mcqs = data_dict.get('questions', []) if isinstance(data_dict, dict) else data_dict

        if not mcqs:
            return jsonify({"error": "Could not extract questions."}), 400

        print(f"✅ Successfully generated {len(mcqs)} questions. Returning to caller.")

        # 5. RETURN DIRECTLY TO CALLER
        return jsonify({"success": True, "data": mcqs})

    except Exception as e:
        traceback.print_exc()
        return jsonify({"success": False, "error": str(e)}), 500


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)