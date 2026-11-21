# Demo presentation

Open [Smart-Healthcare-Demo.pptx](Smart-Healthcare-Demo.pptx) in PowerPoint or another compatible slide editor. It contains eleven editable slides covering roles, architecture, booking and conflict prevention, security, clinical documents, Spring concepts, the independent OpenAI service, verification, and the live demo. Each slide includes English speaker notes.

Allow about seven minutes and use the request order in [the demo guide](../DEMO.md). The verification slide distinguishes automated protocol tests from actual OpenAI generation; do not present an unverified OpenAI model as a successful live demo.

To rebuild the deck after changes, install the presentation-only dependency and run the generator from the project root:

```powershell
python -m pip install -r docs/presentation/requirements.txt
python docs/presentation/build_presentation.py
```

This dependency is not used by either Java application. The generator reads current Surefire reports and an optional ignored `.run/review-points-verification.json` result to populate the evidence slide. Rebuild after running both test suites. The layout uses editable text and shapes; no credentials or patient data are embedded.
