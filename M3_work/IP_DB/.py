import cv2
import time
import ctypes
import tkinter as tk

# ---------------- SETTINGS ----------------
CAMERA_INDEX = 0

# Number of consecutive detections required
# before privacy mode activates.
REQUIRED_FRAMES = 8

# Number of consecutive safe frames required
# before privacy mode turns off.
SAFE_FRAMES = 15

# ------------------------------------------

face_cascade = cv2.CascadeClassifier(
    cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
)

cap = cv2.VideoCapture(CAMERA_INDEX)

if not cap.isOpened():
    print("Could not access the webcam.")
    exit()

privacy_active = False
danger_count = 0
safe_count = 0


def black_screen():
    global privacy_active

    if privacy_active:
        return

    privacy_active = True

    root = tk.Tk()
    root.attributes("-fullscreen", True)
    root.attributes("-topmost", True)
    root.configure(bg="black")

    # Hide mouse cursor
    root.config(cursor="none")

    # Press ESC to close privacy screen
    root.bind("<Escape>", lambda event: root.destroy())

    root.mainloop()


def lock_windows():
    ctypes.windll.user32.LockWorkStation()


print("Privacy Monitor started.")
print("Press Q in the camera window to quit.")

while True:

    ret, frame = cap.read()

    if not ret:
        break

    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

    faces = face_cascade.detectMultiScale(
        gray,
        scaleFactor=1.1,
        minNeighbors=6,
        minSize=(60, 60)
    )

    face_count = len(faces)

    # ----------------------------------
    # MORE THAN ONE PERSON DETECTED
    # ----------------------------------

    if face_count >= 2:

        danger_count += 1
        safe_count = 0

        if danger_count >= REQUIRED_FRAMES:

            print("⚠ Additional person detected!")

            # Activate black screen
            black_screen()

            danger_count = 0

    else:

        danger_count = 0
        safe_count += 1

    # Draw detection boxes
    for (x, y, w, h) in faces:

        cv2.rectangle(
            frame,
            (x, y),
            (x + w, y + h),
            (0, 255, 0),
            2
        )

    cv2.putText(
        frame,
        f"Faces detected: {face_count}",
        (20, 40),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.8,
        (255, 255, 255),
        2
    )

    cv2.imshow("Privacy Monitor", frame)

    if cv2.waitKey(1) & 0xFF == ord("q"):
        break


cap.release()
cv2.destroyAllWindows()
