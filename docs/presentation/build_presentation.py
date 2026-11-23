from pathlib import Path
import json
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
local_tools = ROOT / '.tools' / 'presentation'
if local_tools.exists():
    sys.path.insert(0, str(local_tools))

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.util import Inches, Pt

INK = '16324F'
TEAL = '008C95'
PAPER = 'F4F7FB'
GREY = '52677D'
WHITE = 'FFFFFF'
AMBER = 'E5AD48'
prs = Presentation()
prs.slide_width = Inches(13.333)
prs.slide_height = Inches(7.5)
prs.core_properties.title = 'Smart Healthcare Appointment System'
prs.core_properties.subject = 'Architecture, workflows, Spring concepts, and demonstration'
prs.core_properties.author = 'Smart Healthcare Project'


def box(slide, x, y, w, h, color, rounded=False):
    shape = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE if rounded else MSO_SHAPE.RECTANGLE,
                                   Inches(x), Inches(y), Inches(w), Inches(h))
    shape.fill.solid()
    shape.fill.fore_color.rgb = RGBColor.from_string(color)
    shape.line.fill.background()
    return shape


def text(slide, value, x, y, w, h, size=20, color=INK, bold=False, font='Aptos'):
    frame = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h)).text_frame
    frame.word_wrap = True
    frame.margin_left = frame.margin_right = Inches(0.02)
    for index, line in enumerate(value.split('\n')):
        paragraph = frame.paragraphs[0] if index == 0 else frame.add_paragraph()
        paragraph.text = line
        paragraph.font.name = font
        paragraph.font.size = Pt(size)
        paragraph.font.bold = bold
        paragraph.font.color.rgb = RGBColor.from_string(color)
        paragraph.space_after = Pt(8)
    return frame


def slide(title, eyebrow, note):
    page = prs.slides.add_slide(prs.slide_layouts[6])
    page.background.fill.solid()
    page.background.fill.fore_color.rgb = RGBColor.from_string(PAPER)
    box(page, 0, 0, 13.333, 0.12, TEAL)
    text(page, eyebrow.upper(), 0.65, 0.4, 12, 0.4, 11, TEAL, True)
    text(page, title, 0.65, 0.94, 12, 1.65 if '\n' in title else 0.85, 40 if '\n' in title else 32, INK, True)
    text(page, 'SMART HEALTHCARE  /  PROJECT DEMONSTRATION', 0.65, 7.05, 10.8, 0.22, 9, GREY)
    text(page, f'{len(prs.slides):02d}', 12.05, 7.0, 0.7, 0.3, 12, TEAL, True)
    page.notes_slide.notes_text_frame.text = note
    return page


def cards(page, entries, y=2.25):
    for index, (title, body) in enumerate(entries):
        x = 0.65 + index * 4.1
        box(page, x, y, 3.8, 3.65, WHITE, True)
        box(page, x, y, 3.8, 0.07, TEAL)
        text(page, title, x + 0.22, y + 0.3, 3.35, 0.7, 20, INK, True)
        text(page, body, x + 0.22, y + 1.12, 3.35, 2.2, 17, GREY)


page = slide('Smart Healthcare\nAppointment System', 'Spring Boot REST API',
             'Introduce appointment scheduling and patient-record protection. Explain the implemented features and distinguish actual provider inference from offline demonstration mode.')
text(page, 'Scheduling with database guarantees.\nClinical history with ownership checks.\nExternal AI behind an independent service.', 0.7, 3.0, 7.2, 2.2, 24, GREY)
box(page, 9.1, 2.25, 3.45, 3.6, INK, True)
text(page, 'JAVA 17\nSPRING BOOT 3\nPOSTGRESQL + MONGODB', 9.4, 2.8, 2.9, 2.2, 21, WHITE, True)

page = slide('Three roles, clear responsibilities', 'Users and access',
             'Access depends on both role and ownership. Patients read their own records, doctors access permitted resources, and administrators do not automatically receive clinical-data access.')
cards(page, [('ADMIN', 'Register and manage accounts.\nMaintain doctors and slots.\nPreserve appointment history.'),
             ('DOCTOR', 'Open own daily schedule.\nComplete booked appointments.\nAuthor clinical documents.'),
             ('PATIENT', 'Search by specialty.\nBook or cancel own visits.\nRead own clinical history.')])

page = slide('One API, two data stores, independent AI', 'Architecture',
             'Trace requests through security and application services. The chatbot runs in a separate process without patient-database access. The independent service calls the configured provider over HTTPS when remote mode is enabled.')
nodes = [('HTTP CLIENT', 0.65, 2.45, 2.3, INK), ('HEALTHCARE API', 3.45, 2.45, 3.1, TEAL),
         ('POSTGRESQL', 8.0, 2.0, 4.1, INK), ('MONGODB', 8.0, 3.2, 4.1, INK),
         ('CHATBOT SERVICE', 3.45, 4.65, 3.1, TEAL), ('GROQ API', 8.0, 4.65, 4.1, INK)]
for label, x, y, w, color in nodes:
    box(page, x, y, w, 0.8, color, True)
    text(page, label, x + 0.12, y + 0.2, w - 0.24, 0.4, 17, WHITE, True)
for label, x, y in [('>', 3.03, 2.63), ('>', 7.1, 2.05), ('>', 7.1, 3.25), ('v', 4.85, 3.6), ('>', 7.1, 4.8)]:
    text(page, label, x, y, 0.5, 0.5, 26, TEAL, True)
text(page, 'Relational scheduling          Document history          No clinical DB access from AI', 0.7, 6.15, 12, 0.5, 17, GREY)

page = slide('Book, cancel, rebook, complete', 'Appointment workflow',
             'Demonstrate the workflow in Postman. Booking uses the authenticated patient identity. Cancellation releases the slot while preserving history; completion enables prescription creation.')
steps = [('01', 'Search doctor'), ('02', 'Choose an available slot'), ('03', 'Book as authenticated patient'),
         ('04', 'Cancel and preserve history'), ('05', 'Another patient can rebook'), ('06', 'Doctor completes the visit')]
for index, (number, label) in enumerate(steps):
    x = 0.65 + (index % 3) * 4.1
    y = 2.2 + (index // 3) * 1.85
    box(page, x, y, 3.8, 1.5, WHITE, True)
    text(page, number, x + 0.2, y + 0.2, 0.6, 0.5, 23, TEAL, True)
    text(page, label, x + 0.9, y + 0.3, 2.6, 1.1, 20)
text(page, 'GET /api/appointments/me?date=today   |   UTC calendar day', 0.75, 6.2, 12, 0.5, 18, GREY)

page = slide('Conflicting bookings cannot both succeed', 'Concurrency and logging',
             'The early availability check improves feedback; the partial unique index guarantees concurrent booking safety. Show the doctor, UTC appointment time, date and request ID in the conflict log, without patient or clinical content.')
text(page, 'Two patients request the same slot', 0.8, 2.0, 11.6, 0.55, 25, INK, True)
cards(page, [('DATABASE GUARANTEE', 'One occupied slot.\nPartial unique index.\nOne 201 and one 409.'),
             ('STATE TRANSITIONS', 'Pessimistic row locks.\nCancellation and completion cannot overwrite each other.'),
             ('SAFE DIAGNOSTICS', 'Doctor and UTC slot time.\nRequest ID and outcome.\nNo patient or clinical content.')], y=2.8)
text(page, 'Double booking attempt prevented for Dr. Smith at 10:00 AM UTC (2030-01-07)', 0.8, 6.55, 11.9, 0.4, 14, TEAL, True, 'Consolas')

page = slide('Identity, permission, ownership', 'Security',
             'Explain Basic Auth and the need for HTTPS beyond localhost. BCrypt protects stored passwords. CSRF protection is separate from authentication, and the server enforces permissions.')
cards(page, [('AUTHENTICATION', 'HTTP Basic Auth.\nBCrypt password hashes.\nEnabled-account checks.'),
             ('AUTHORIZATION', 'Role-restricted routes.\nService-level checks.\nOwnership enforcement.'),
             ('REQUEST PROTECTION', 'Fresh CSRF for writes.\nValidated DTOs.\nSafe error responses.')])
text(page, 'HTTPS is required beyond localhost. Session/JWT migration is not part of this implementation.', 0.75, 6.35, 12, 0.5, 16, GREY)

page = slide('Clinical documents keep their history', 'MongoDB and optimistic concurrency',
             'Prescription creation requires a completed appointment belonging to the doctor. Documents reference relational IDs, and concurrent updates are checked using the document version.')
cards(page, [('PRESCRIPTIONS', 'Medicines and notes.\nCompleted appointment required.\nPatient and author ownership.'),
             ('MEDICAL RECORDS', 'Notes and lab references.\nStable relational IDs.\nNo clinical deletion endpoint.'),
             ('VERSION CHECKS', 'Client supplies version.\nOne competing update wins.\nStale updates return 409.')])

page = slide('Spring concepts used for a purpose', 'Dependency injection and lifecycle',
             'Connect each concept to the implementation: constructor injection, request-scoped log context, transaction-aware AOP logging, and a bounded cache with an explicit lifecycle.')
cards(page, [('IoC AND SCOPES', 'Constructor injection.\nSingleton application services.\nRequest-scoped log ID.'),
             ('AOP', 'Attempts and outcomes.\nTransaction-aware logging.\nOriginal exceptions preserved.'),
             ('HIBERNATE CACHE', 'L1 persistence context.\nBounded doctor L2 cache.\nCommitted writes refresh data.')])

page = slide('Authenticated role selects an AI model', 'Independent chatbot service',
             'Show that the authenticated account determines the role. The independent service selects the configured model and calls the external provider API. It cannot execute application actions; its intended scope is workflow guidance, not diagnosis.')
cards(page, [('ADMIN', 'openai/gpt-oss-20b\nAccount and slot workflow guidance.'),
             ('DOCTOR', 'openai/gpt-oss-120b\nAppointment and document workflow guidance.'),
             ('PATIENT', 'qwen/qwen3.8-27b\nBooking and personal-history guidance.')])
text(page, 'Private service key  |  External inference  |  Configurable models  |  No clinical database access', 0.75, 6.25, 12, 0.55, 16, GREY)


def test_count(directory):
    reports = [ET.parse(p).getroot() for p in directory.glob('TEST-*.xml')]
    return sum(int(report.attrib['tests']) for report in reports)


main_tests = test_count(ROOT / 'target/surefire-reports')
chat_tests = test_count(ROOT / 'chatbot-service/target/surefire-reports')
verification_path = ROOT / '.run/review-points-verification.json'
verification = json.loads(verification_path.read_text(encoding='utf-8-sig')) if verification_path.exists() else {}
page = slide('Evidence from tests and live requests', 'Verification',
             'Use recorded test results as evidence. Distinguish provider-fixture protocol checks from actual provider generation. Do not claim an inference result that has not been verified.')
cards(page, [('BACKEND TESTS', f'{main_tests} tests\nReal PostgreSQL/MongoDB cases.\nOwnership and concurrent writes.'),
             ('CHATBOT SERVICE', f'{chat_tests} tests\nModel routing and private key.\nProvider failure handling.'),
             ('LIVE CHECKS', verification.get('summary', 'Provider generation is awaiting verification.\nSee TESTING.md for current evidence.'))])

page = slide('Demonstrate the complete journey', 'Seven-minute walkthrough',
             'Finish with conflicting bookings, appointment completion, prescription creation and patient history. Demonstrate Groq inference with synthetic messages and inspect the server-selected model. This is an educational project, and generated guidance must not be treated as medical advice.')
text(page, '1   Admin creates doctor, patient and slot.\n2   Patient books; a second booking is rejected and logged.\n3   Doctor opens the schedule and completes the visit.\n4   Doctor creates a prescription; patient reads own history.\n5   Ask the AI service as each role and inspect the selected model.', 0.85, 2.2, 11.5, 3.55, 23)
box(page, 0.7, 6.0, 11.95, 0.65, TEAL, True)
text(page, 'Project scope: educational REST API; no frontend or autonomous clinical decisions.', 0.95, 6.15, 11.4, 0.4, 17, WHITE)

destination = ROOT / 'docs/presentation/Smart-Healthcare-Demo.pptx'
prs.save(destination)
validated = Presentation(destination)
assert len(validated.slides) == 11
for page in validated.slides:
    assert page.notes_slide.notes_text_frame.text.strip()
    for shape in page.shapes:
        assert shape.left >= 0 and shape.top >= 0
        assert shape.left + shape.width <= prs.slide_width + Inches(0.02)
        assert shape.top + shape.height <= prs.slide_height + Inches(0.02)
print(f'Created and structurally validated {len(validated.slides)} editable slides: {destination}')
