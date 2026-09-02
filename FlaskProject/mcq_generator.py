import random
import re
import spacy
import nltk
import joblib
import ollama  # <--- INTEGRATED LLAMA 3
import json
from nltk.corpus import wordnet
from sentence_transformers import SentenceTransformer
from sklearn.metrics.pairwise import cosine_similarity

# Ensure wordnet is available
nltk.download('wordnet', quiet=True)
nlp = spacy.load("en_core_web_sm")

# Load lightweight embedding model
embed_model = SentenceTransformer("all-MiniLM-L6-v2")

# Load the trained classifier
try:
    clf = joblib.load('distractor_classifier.pkl')
    print("✅ Successfully loaded trained distractor classifier.")
except FileNotFoundError:
    print("⚠️ Warning: distractor_classifier.pkl not found. Falling back to dummy logic.")
    clf = None


def normalize(text):
    return re.sub(r'\s+', ' ', text).strip()


def extract_sentences(text):
    doc = nlp(text)
    return [s.text.strip() for s in doc.sents if s.text.strip()]


def mask_phrase(sentence, phrase):
    if not phrase:
        return sentence
    pattern = re.compile(r'\b' + re.escape(phrase) + r'\b', flags=re.IGNORECASE)
    masked, count = pattern.subn('____', sentence, count=1)
    if count: return masked
    tokens = phrase.split()
    for t in tokens:
        pattern2 = re.compile(r'\b' + re.escape(t) + r'\b', flags=re.IGNORECASE)
        masked2, c2 = pattern2.subn('____', sentence, count=1)
        if c2: return masked2
    return sentence


def collect_candidates(sentences, target_pos):
    candidates = set()
    for s in sentences:
        doc = nlp(s)
        for tok in doc:
            if tok.pos_ in target_pos and tok.is_alpha and len(tok.text) > 2:
                candidates.add(tok.text)
    return list(candidates)


def wordnet_candidates(answer):
    ans = re.sub(r'[^A-Za-z]', ' ', answer).lower().strip()
    lemmas = set()
    for syn in wordnet.synsets(ans):
        for lemma in syn.lemmas():
            name = lemma.name().replace('_', ' ')
            name = re.sub(r'[^A-Za-z\s]', '', name).strip()
            if name and name.lower() != ans and len(name) > 2:
                lemmas.add(name)
    return list(lemmas)


def score_distractor(passage, answer, question, distractor):
    emb_passage = embed_model.encode(passage)
    emb_answer = embed_model.encode(answer)
    emb_question = embed_model.encode(question)
    emb_distractor = embed_model.encode(distractor)

    sim_to_passage = cosine_similarity([emb_distractor], [emb_passage])[0][0]
    sim_to_answer = cosine_similarity([emb_distractor], [emb_answer])[0][0]
    sim_to_question = cosine_similarity([emb_distractor], [emb_question])[0][0]

    return [sim_to_passage, sim_to_answer, sim_to_question]


# --- LLAMA 3 INTEGRATION ---
def build_distractors(answer, question, sentences, max_distractors=3):
    answer_norm = normalize(answer)
    doc = nlp(answer_norm)
    target_pos = ("NOUN", "PROPN")

    candidates = collect_candidates(sentences, target_pos)
    candidates = [c for c in candidates if c.lower() != answer_norm.lower() and len(c) > 2]
    wn = wordnet_candidates(answer_norm)
    raw_distractors = candidates + wn

    # 1. Try your ML Pipeline first
    scored = []
    for d in raw_distractors:
        features = score_distractor(" ".join(sentences), answer_norm, question, d)
        if clf is not None:
            try:
                quality = clf.predict([features])[0]
                if quality == 1: scored.append(d)
            except Exception:
                pass
        else:
            if 0.2 < features[1] < 0.7: scored.append(d)

    final_distractors = [normalize(d) for d in scored[:max_distractors]]

    # 2. Use Llama 3 to fill in missing distractors
    if len(final_distractors) < max_distractors:
        missing_count = max_distractors - len(final_distractors)
        prompt = f"""
        Question: "{question}"
        Correct Answer: "{answer}"
        Task: Provide exactly {missing_count} highly plausible but incorrect multiple-choice options.
        Output ONLY a JSON array of strings. Example: ["wrong option 1", "wrong option 2"]
        """
        try:
            print(f"🧠 ML found {len(final_distractors)} distractors. Asking Llama 3 for {missing_count} more...")
            response = ollama.chat(model='llama3', messages=[{'role': 'user', 'content': prompt}])
            out = response['message']['content'].strip()
        except Exception:
                print(Exception)
            # Clean markdown
           # if out.startswith("http://googleusercontent.com / immersive_entry_chip / 0,http: // googleusercontent.com / immersive_entry_chip / 1")