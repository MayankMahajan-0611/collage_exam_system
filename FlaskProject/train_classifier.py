import pandas as pd
import numpy as np
import random
import joblib
from sentence_transformers import SentenceTransformer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics.pairwise import cosine_similarity
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report

print("Step 1/6: Loading Embedding Model...")
embed_model = SentenceTransformer("all-MiniLM-L6-v2")

print("Step 2/6: Loading local 'train.csv' dataset...")
try:
    # Make sure train.csv is in your PyCharm project folder!
    df = pd.read_csv("D:/projects/archive/train.csv")
except FileNotFoundError:
    print("❌ Error: 'train.csv' not found. Make sure it is in the same folder as this script.")
    exit()

df = df.dropna(subset=['support'])
df = df.sample(n=min(5000, len(df)), random_state=42)

X_features = []
y_labels = []

print(f"Step 3/6: Extracting 3 features (Passage, Answer, Question) for {len(df)} rows...")
for index, row in df.iterrows():
    passage = str(row['support'])
    answer = str(row['correct_answer'])
    question = str(row['question'])

    emb_passage = embed_model.encode(passage)
    emb_answer = embed_model.encode(answer)
    emb_question = embed_model.encode(question)

    # --- POSITIVE EXAMPLES (Label = 1: Good Distractors) ---
    good_distractors = [str(row['distractor1']), str(row['distractor2']), str(row['distractor3'])]

    for distractor in good_distractors:
        if distractor.lower() == 'nan':
            continue

        emb_distractor = embed_model.encode(distractor)
        sim_to_passage = cosine_similarity([emb_distractor], [emb_passage])[0][0]
        sim_to_answer = cosine_similarity([emb_distractor], [emb_answer])[0][0]
        sim_to_q = cosine_similarity([emb_distractor], [emb_question])[0][0]

        X_features.append([sim_to_passage, sim_to_answer, sim_to_q])
        y_labels.append(1)

        # --- NEGATIVE EXAMPLES (Label = 0: Bad Distractors) ---
    for _ in range(3):
        random_row = df.sample(1).iloc[0]
        bad_distractor = str(random_row['correct_answer'])

        emb_bad_distractor = embed_model.encode(bad_distractor)
        sim_bad_to_passage = cosine_similarity([emb_bad_distractor], [emb_passage])[0][0]
        sim_bad_to_answer = cosine_similarity([emb_bad_distractor], [emb_answer])[0][0]
        sim_bad_to_q = cosine_similarity([emb_bad_distractor], [emb_question])[0][0]

        X_features.append([sim_bad_to_passage, sim_bad_to_answer, sim_bad_to_q])
        y_labels.append(0)

X = np.array(X_features)
y = np.array(y_labels)

print("Step 4/6: Splitting data into training and testing sets...")
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

print("Step 5/6: Training Logistic Regression Model...")
clf = LogisticRegression(class_weight='balanced')
clf.fit(X_train, y_train)

y_pred = clf.predict(X_test)
print("\n--- Model Evaluation ---")
print(classification_report(y_test, y_pred))

print("\nStep 6/6: Saving the trained model...")
joblib.dump(clf, 'distractor_classifier.pkl')
print("✅ Success! Model saved as 'distractor_classifier.pkl'")